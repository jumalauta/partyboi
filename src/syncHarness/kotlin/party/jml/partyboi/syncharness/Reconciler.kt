package party.jml.partyboi.syncharness

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import party.jml.partyboi.sync.SyncedTable
import party.jml.partyboi.sync.Table

/**
 * Phase E: after syncUp the master holds two copies of frank and of his prod
 * (one per instance). This drives the admin reconciliation flow on the master —
 * verify the candidates are detected, merge users then entries through the
 * admin endpoints, and assert the merged state including vote reassignment.
 */
class Reconciler(
    private val master: InstanceClient,
    private val masterToken: String,
) {
    suspend fun run(duplicateTitle: String, duplicateUserName: String) {
        // --- Locate the two copies by origin ---------------------------------
        val entries = readTable(SyncedTable.Entries)
        val entryCopies = entries.data.filter { str(it, "title") == duplicateTitle }
        require(entryCopies.size == 2) {
            "Expected 2 copies of '$duplicateTitle' on master after syncUp, found ${entryCopies.size}"
        }
        val survivorEntry = entryCopies.byOrigin("master")
        val loserEntry = entryCopies.byOrigin("remote")

        val users = readTable(SyncedTable.Users)
        val userCopies = users.data.filter {
            val name = str(it, "name")
            name == duplicateUserName || name?.startsWith("$duplicateUserName-") == true
        }
        require(userCopies.size == 2) {
            "Expected 2 copies of user '$duplicateUserName' on master after syncUp, found ${userCopies.size}"
        }
        val survivorUser = userCopies.byOrigin("master")
        val loserUser = userCopies.byOrigin("remote")
        println("[reconcile] entry copies: master=$survivorEntry remote=$loserEntry")
        println("[reconcile] user copies: master=$survivorUser remote=$loserUser")

        // --- The reconcile page proposes both pairs --------------------------
        val page = master.getHtml("/sync/reconcile")
        require(page.contains(loserEntry)) {
            "/sync/reconcile does not list the duplicate entry pair (missing id $loserEntry)"
        }
        require(page.contains(loserUser)) {
            "/sync/reconcile does not list the duplicate user pair (missing id $loserUser)"
        }
        println("[reconcile] /sync/reconcile lists both candidate pairs")

        // --- Snapshot votes, then merge users first, then entries ------------
        val votesBefore = readVotes()

        master.postMultipart("/sync/reconcile/user/$survivorUser/merge/$loserUser", emptyList()).expectOk()
        println("[reconcile] merged user $loserUser into $survivorUser")
        master.postMultipart("/sync/reconcile/entry/$survivorEntry/merge/$loserEntry", emptyList()).expectOk()
        println("[reconcile] merged entry $loserEntry into $survivorEntry")

        // --- Assert merged state ---------------------------------------------
        val entriesAfter = readTable(SyncedTable.Entries)
        val copiesAfter = entriesAfter.data.filter { str(it, "title") == duplicateTitle }
        require(copiesAfter.size == 1 && str(copiesAfter.single(), "id") == survivorEntry) {
            "Expected exactly the surviving copy of '$duplicateTitle' after merge"
        }
        require(entriesAfter.data.size == entries.data.size - 1) {
            "Expected entry count to drop by one (was ${entries.data.size}, is ${entriesAfter.data.size})"
        }

        val usersAfter = readTable(SyncedTable.Users)
        val userNamesAfter = usersAfter.data.mapNotNull { str(it, "name") }
            .filter { it == duplicateUserName || it.startsWith("$duplicateUserName-") }
        require(userNamesAfter == listOf(duplicateUserName)) {
            "Expected a single user '$duplicateUserName' after merge, found $userNamesAfter"
        }

        // Both uploaded files must now hang off the surviving entry.
        val entryFiles = readTable(SyncedTable.EntryFiles)
        val survivorFiles = entryFiles.data.count { str(it, "entry_id") == survivorEntry }
        require(survivorFiles == 2) {
            "Expected the surviving entry to hold both file versions, found $survivorFiles"
        }

        // Votes: same rows as before, with the loser user/entry ids mapped onto the
        // survivors; on (user, entry) collisions the higher points win.
        val expectedVotes = votesBefore
            .map { (user, entry, points) ->
                Triple(
                    if (user == loserUser) survivorUser else user,
                    if (entry == loserEntry) survivorEntry else entry,
                    points,
                )
            }
            .groupBy { it.first to it.second }
            .map { (key, rows) -> Triple(key.first, key.second, rows.maxOf { it.third }) }
            .toSet()
        val votesAfter = readVotes().toSet()
        require(votesAfter == expectedVotes) {
            "Votes after merge do not match expectation.\n" +
                    "missing: ${expectedVotes - votesAfter}\nunexpected: ${votesAfter - expectedVotes}"
        }
        println("[reconcile] merged state verified: entries, users, files and ${votesAfter.size} votes check out")
    }

    private suspend fun readTable(table: SyncedTable): Table =
        master.getJsonWithToken("/sync/table/${table.name.lowercase()}", masterToken)

    private suspend fun readVotes(): List<Triple<String, String, Long>> =
        readTable(SyncedTable.Votes).data.map { row ->
            Triple(
                str(row, "user_id") ?: error("vote row without user_id"),
                str(row, "entry_id") ?: error("vote row without entry_id"),
                row["points"]?.jsonPrimitive?.longOrNull ?: error("vote row without points"),
            )
        }

    private fun str(row: Map<String, *>, key: String): String? =
        (row[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content

    private fun List<Map<String, kotlinx.serialization.json.JsonElement>>.byOrigin(origin: String): String =
        firstOrNull { str(it, "origin") == origin }?.let { str(it, "id")!! }
            ?: error("No row with origin '$origin' among the duplicate copies")
}
