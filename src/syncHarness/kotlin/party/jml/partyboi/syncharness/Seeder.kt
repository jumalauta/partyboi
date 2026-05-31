package party.jml.partyboi.syncharness

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import party.jml.partyboi.sync.SyncedTable
import party.jml.partyboi.sync.Table

/**
 * Two-phase scenario seeder. preParty seeds the master with a pre-party cohort: an admin,
 * five users registering through the public portal, several entries spread across two compos.
 * partyDay then runs against the remote after a syncDown: three new users register at the
 * party, submit their entries, then all eight users register a vote key and cast votes.
 */
class Seeder(
    private val adminName: String = "admin",
    private val adminPassword: String = "password",
) {

    data class UserInfo(val id: String, val name: String, val password: String)
    data class EntryInfo(val id: String, val title: String, val compoId: String)

    data class PrePartyResult(
        val syncToken: String,
        val demoCompoId: String,
        val musicCompoId: String,
        val preUsers: List<UserInfo>,
        val preEntries: List<EntryInfo>,
    )

    data class PartyDayResult(
        val partyUsers: List<UserInfo>,
        val partyEntries: List<EntryInfo>,
        val votesCast: Int,
    )

    suspend fun preParty(master: InstanceClient): PrePartyResult {
        println("[seed/preParty] Logging in as $adminName on master")
        master.login(adminName, adminPassword)

        println("[seed/preParty] Generating master sync token")
        val syncToken = master.generateSyncToken()

        println("[seed/preParty] Creating compos")
        master.postMultipart("/admin/compos", listOf("name" to "Demo", "rules" to "Make a demo")).expectOk()
        master.postMultipart("/admin/compos", listOf("name" to "Music", "rules" to "Make a song")).expectOk()

        val compos = readTable(master, syncToken, SyncedTable.Compos)
        val demoCompoId = compoIdByName(compos, "Demo")
        val musicCompoId = compoIdByName(compos, "Music")
        println("[seed/preParty] Demo=$demoCompoId Music=$musicCompoId")

        println("[seed/preParty] Opening submissions on both compos")
        master.put("/admin/compos/$demoCompoId/setSubmit/true").expectOk()
        master.put("/admin/compos/$musicCompoId/setSubmit/true").expectOk()

        println("[seed/preParty] Adding welcome slide and pre-printing 20 vote keys")
        master.postMultipart(
            "/admin/screen/default/new/textslide",
            listOf("title" to "Welcome to the party", "content" to "Have fun!"),
        ).expectOk()
        master.postMultipart("/admin/voting/generate", listOf("numberOfKeys" to "20")).expectOk()

        // Five users register through the public portal, total seven entries across both compos.
        val preCohort = listOf(
            UploadPlan("alice", "alicepass1", listOf("Glorious Demo" to demoCompoId)),
            UploadPlan("bob", "bobpass11", listOf("Late Night Beats" to musicCompoId)),
            UploadPlan(
                "carol",
                "carolpass1",
                listOf("Pixel Adventure" to demoCompoId, "Chiptune Anthem" to musicCompoId),
            ),
            UploadPlan("dave", "davepass1", listOf("Drumloop King" to musicCompoId)),
            UploadPlan("eve", "evepass111", listOf("Polygon Dreams" to demoCompoId)),
        )

        val preUsers = mutableListOf<UserInfo>()
        for (plan in preCohort) {
            preUsers += registerAndSubmit(master, syncToken, plan)
        }

        val entries = readTable(master, syncToken, SyncedTable.Entries)
        val preEntries = preCohort.flatMap { plan ->
            plan.entries.map { (title, compoId) ->
                EntryInfo(entryIdByTitle(entries, title), title, compoId)
            }
        }
        println("[seed/preParty] ${preUsers.size} users, ${preEntries.size} entries seeded on master")

        println("[seed/preParty] Closing submissions on both compos (party is starting)")
        master.put("/admin/compos/$demoCompoId/setSubmit/false").expectOk()
        master.put("/admin/compos/$musicCompoId/setSubmit/false").expectOk()

        printRowCounts(master, syncToken, "master after pre-party seeding")

        return PrePartyResult(
            syncToken = syncToken,
            demoCompoId = demoCompoId,
            musicCompoId = musicCompoId,
            preUsers = preUsers,
            preEntries = preEntries,
        )
    }

    suspend fun partyDay(remote: InstanceClient, pre: PrePartyResult): PartyDayResult {
        println("[seed/partyDay] Logging in as $adminName on remote (local admin, not master's)")
        remote.login(adminName, adminPassword)

        println("[seed/partyDay] Reopening Demo submissions on the remote only (the party hall is staffed)")
        remote.put("/admin/compos/${pre.demoCompoId}/setSubmit/true").expectOk()

        val partyCohort = listOf(
            UploadPlan("frank", "frankpass1", listOf("Last Minute Demo" to pre.demoCompoId)),
            UploadPlan("grace", "gracepass1", listOf("Onsite Jam" to pre.musicCompoId)),
            UploadPlan("heidi", "heidipass1", listOf("Hall Floor Hack" to pre.demoCompoId)),
        )
        // grace's Music entry needs submitting open on Music too.
        remote.put("/admin/compos/${pre.musicCompoId}/setSubmit/true").expectOk()

        val partyUsers = mutableListOf<UserInfo>()
        for (plan in partyCohort) {
            partyUsers += registerAndSubmit(remote, pre.syncToken, plan)
        }

        println("[seed/partyDay] Closing submissions, opening voting")
        remote.put("/admin/compos/${pre.demoCompoId}/setSubmit/false").expectOk()
        remote.put("/admin/compos/${pre.musicCompoId}/setSubmit/false").expectOk()
        remote.put("/admin/compos/${pre.demoCompoId}/setVoting/true").expectOk()
        remote.put("/admin/compos/${pre.musicCompoId}/setVoting/true").expectOk()

        val partyEntries = partyCohort.flatMap { plan ->
            // Re-read the remote's entries table to pick up the freshly-submitted rows.
            val entries = readTable(remote, pre.syncToken, SyncedTable.Entries)
            plan.entries.map { (title, compoId) ->
                EntryInfo(entryIdByTitle(entries, title), title, compoId)
            }
        }

        // Find unregistered vote keys on the remote (they synced down from master with user_id=null).
        // JsonNull is a JsonPrimitive whose `content` is the literal string "null", not Kotlin null
        // — so check the element type directly instead of going via .content.
        val voteKeys = readTable(remote, pre.syncToken, SyncedTable.VoteKeys)
        val freeKeyCodes = voteKeys.data
            .filter { row -> row["user_id"].let { it == null || it is JsonNull } }
            .mapNotNull { (it["key"] as? JsonPrimitive)?.content?.substringAfter("ticket:") }

        val allEntries = pre.preEntries + partyEntries
        val voters = pre.preUsers + partyUsers
        require(freeKeyCodes.size >= voters.size) {
            "Not enough vote keys: have ${freeKeyCodes.size}, need ${voters.size}"
        }

        // Each voter picks a ballot covering ~half the entries with varied points.
        var votesCast = 0
        for ((idx, voter) in voters.withIndex()) {
            val keyCode = freeKeyCodes[idx]
            val ballot = pickBallot(idx, allEntries)
            votesCast += castVotesAs(remote, voter, keyCode, ballot)
        }

        println("[seed/partyDay] Closing voting")
        remote.put("/admin/compos/${pre.demoCompoId}/setVoting/false").expectOk()
        remote.put("/admin/compos/${pre.musicCompoId}/setVoting/false").expectOk()

        printRowCounts(remote, pre.syncToken, "remote after party-day activity")

        return PartyDayResult(
            partyUsers = partyUsers,
            partyEntries = partyEntries,
            votesCast = votesCast,
        )
    }

    private data class UploadPlan(
        val name: String,
        val password: String,
        val entries: List<Pair<String, String>>, // (title, compoId)
    )

    private suspend fun registerAndSubmit(instance: InstanceClient, syncToken: String, plan: UploadPlan): UserInfo {
        require(plan.password.length >= 8) { "Password for ${plan.name} must be at least 8 chars" }
        InstanceClient("${instance.label}-as-${plan.name}", instance.baseUrl).use { uc ->
            uc.register(plan.name, plan.password, email = "")
            // Confirm registration before attempting to submit entries — /register on
            // validation failure renders an HTML error page with status 200, which expectOk()
            // would happily accept.
            run {
                val users: Table = instance.getJsonWithToken("/sync/table/users", syncToken)
                if (users.data.none { (it["name"] as? JsonPrimitive)?.content == plan.name }) {
                    error("Registration failed for ${plan.name} — user not present after POST /register (check password rules / captcha)")
                }
            }
            for ((idx, titled) in plan.entries.withIndex()) {
                val (title, compoId) = titled
                val author = plan.name.replaceFirstChar { it.titlecase() } + " / Group${plan.name.hashCode().and(0xFF)}"
                val fileBytes = randomBytes(1024 + plan.name.hashCode().and(0x3FF), seed = (plan.name.hashCode().toLong() shl 8) or idx.toLong())
                uc.postMultipartWithFile(
                    path = "/entries",
                    fields = listOf(
                        "compoId" to compoId,
                        "title" to title,
                        "author" to author,
                        "screenComment" to "Greetings from ${plan.name}",
                        "orgComment" to "Please run on hardware if possible",
                    ),
                    fileField = "file",
                    fileName = "${plan.name}-${title.replace(' ', '_').lowercase()}.bin",
                    fileBytes = fileBytes,
                ).expectOk()
            }
        }
        val users: Table = instance.getJsonWithToken("/sync/table/users", syncToken)
        val userRow = users.data.firstOrNull { (it["name"] as? JsonPrimitive)?.content == plan.name }
            ?: error("User ${plan.name} not found after register")
        val id = (userRow["id"] as JsonPrimitive).content
        println("[seed] Registered ${plan.name} (id=$id) with ${plan.entries.size} entries")
        return UserInfo(id, plan.name, plan.password)
    }

    private suspend fun castVotesAs(
        instance: InstanceClient,
        voter: UserInfo,
        voteKeyCode: String,
        ballot: List<Pair<String, Int>>,
    ): Int {
        InstanceClient("${instance.label}-as-${voter.name}", instance.baseUrl).use { uc ->
            uc.login(voter.name, voter.password)
            uc.postMultipart("/vote/register", listOf("code" to voteKeyCode)).expectOk()
            for ((entryId, points) in ballot) {
                uc.put("/vote/$entryId/$points").expectOk()
            }
        }
        return ballot.size
    }

    /**
     * Pick a deterministic but varied ballot per voter: every voter casts points on a rotating
     * window of entries with point values that vary by voter index. Self-votes are allowed
     * (partyboi permits them today; if that ever changes the harness will fail loudly here).
     */
    private fun pickBallot(voterIdx: Int, entries: List<EntryInfo>): List<Pair<String, Int>> {
        val ballotSize = (entries.size / 2).coerceAtLeast(3)
        return (0 until ballotSize).map { i ->
            val entry = entries[(voterIdx + i) % entries.size]
            val points = ((voterIdx + i) % 5) + 1
            entry.id to points
        }
    }

    private suspend fun readTable(instance: InstanceClient, token: String, table: SyncedTable): Table =
        instance.getJsonWithToken("/sync/table/${table.name.lowercase()}", token)

    private fun compoIdByName(compos: Table, name: String): String =
        compos.data.firstOrNull { (it["name"] as? JsonPrimitive)?.content == name }
            ?.let { (it["id"] as JsonPrimitive).content }
            ?: error("Compo '$name' not found in compo table")

    private fun entryIdByTitle(entries: Table, title: String): String =
        entries.data.firstOrNull { (it["title"] as? JsonPrimitive)?.content == title }
            ?.let { (it["id"] as JsonPrimitive).content }
            ?: error("Entry '$title' not found in entry table")

    private suspend fun printRowCounts(instance: InstanceClient, token: String, label: String) {
        println("[seed] Row counts ($label):")
        for (table in SyncedTable.entries) {
            val t: Table = instance.getJsonWithToken("/sync/table/${table.name.lowercase()}", token)
            println("  ${table.name.padEnd(20)} -> ${t.data.size}")
        }
    }

    private fun randomBytes(size: Int, seed: Long): ByteArray {
        val out = ByteArray(size)
        java.util.Random(seed).nextBytes(out)
        return out
    }
}
