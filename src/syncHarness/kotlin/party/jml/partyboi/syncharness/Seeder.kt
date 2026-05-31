package party.jml.partyboi.syncharness

import kotlinx.serialization.json.JsonPrimitive
import party.jml.partyboi.sync.SyncedTable
import party.jml.partyboi.sync.Table

data class SeedResult(
    val syncToken: String,
    val compoId: String,
    val entryId: String,
    val johnUserId: String,
)

/**
 * Drives the master instance via existing HTTP routes to create a non-trivial mock dataset:
 * an admin, a user "john", a "Demo" compo with submitting + voting open, an entry with a
 * binary file and a preview image, a schedule event, a slide, vote keys, and a cast vote.
 *
 * Returns a SeedResult with the freshly generated sync token + a handful of UUIDs so the
 * verifier can spot-check specific records without having to rediscover them.
 */
class Seeder(
    private val master: InstanceClient,
    private val adminName: String = "admin",
    private val adminPassword: String = "password",
    private val johnName: String = "john",
    private val johnPassword: String = "johnpassword",
) {
    suspend fun seed(): SeedResult {
        println("[seed] Logging in as $adminName")
        master.login(adminName, adminPassword)

        println("[seed] Generating sync token on master")
        val syncToken = master.generateSyncToken()

        println("[seed] Creating Demo compo")
        master.postMultipart(
            "/admin/compos",
            listOf("name" to "Demo", "rules" to "Make a demo about it"),
        ).expectOk()

        println("[seed] Creating Music compo")
        master.postMultipart(
            "/admin/compos",
            listOf("name" to "Music", "rules" to "Make a song about it"),
        ).expectOk()

        val compos = readTable(syncToken, SyncedTable.Compos)
        val demoCompo = compos.data.firstOrNull { (it["name"] as? JsonPrimitive)?.content == "Demo" }
            ?: error("[seed] Demo compo not found after creation")
        val demoCompoId = (demoCompo["id"] as JsonPrimitive).content
        println("[seed] Demo compo id = $demoCompoId")

        println("[seed] Opening submissions on Demo compo")
        master.put("/admin/compos/$demoCompoId/setSubmit/true").expectOk()

        println("[seed] Registering user '$johnName'")
        // Re-login as admin afterward; /register sets john's session on this client.
        val johnClient = InstanceClient("master-as-john", master.baseUrl)
        johnClient.use { jc ->
            jc.register(johnName, johnPassword, email = "")

            // Just in case the /register flow needs an explicit login (it shouldn't — the route sets the session).
            // We verify by hitting / which redirects to / if logged in.
            val johnFile = randomBytes(1024)
            println("[seed] Submitting entry as john")
            jc.postMultipartWithFile(
                path = "/entries",
                fields = listOf(
                    "compoId" to demoCompoId,
                    "title" to "My Glorious Demo",
                    "author" to "John Doe / Jumalauta",
                    "screenComment" to "Greez to world healerz!",
                    "orgComment" to "I love you!",
                ),
                fileField = "file",
                fileName = "demo.xxx",
                fileBytes = johnFile,
            ).expectOk()
        }

        val entries = readTable(syncToken, SyncedTable.Entries)
        val entry = entries.data.firstOrNull { (it["title"] as? JsonPrimitive)?.content == "My Glorious Demo" }
            ?: error("[seed] Demo entry not found after creation")
        val entryId = (entry["id"] as JsonPrimitive).content
        val johnUserId = (entry["user_id"] as JsonPrimitive).content
        println("[seed] Demo entry id = $entryId, john user id = $johnUserId")

        println("[seed] Uploading preview image for entry $entryId (as admin)")
        val previewBytes = this::class.java.getResourceAsStream("/preview.png")?.readBytes()
            ?: error("[seed] /preview.png resource not found on syncHarness classpath")
        master.postMultipartWithFile(
            path = "/entries/$entryId/preview",
            fields = emptyList(),
            fileField = "file",
            fileName = "preview.png",
            fileBytes = previewBytes,
            fileContentType = "image/png",
        ).expectOk()

        println("[seed] Creating schedule event")
        val startIso = "2030-01-01T12:00:00Z"
        val endIso = "2030-01-01T13:00:00Z"
        master.postMultipart(
            "/admin/schedule/events",
            listOf(
                "name" to "Deadline for demo compo",
                "startTime" to startIso,
                "endTime" to endIso,
                "visible" to "true",
            ),
        ).expectOk()

        println("[seed] Adding TextSlide to default slide set")
        master.postMultipart(
            "/admin/screen/default/new/textslide",
            listOf("title" to "Hello, world!", "content" to "Welcome to the party!"),
        ).expectOk()

        println("[seed] Generating 10 vote keys")
        master.postMultipart(
            "/admin/voting/generate",
            listOf("numberOfKeys" to "10"),
        ).expectOk()

        println("[seed] Opening voting on Demo compo")
        master.put("/admin/compos/$demoCompoId/setSubmit/false").expectOk()
        master.put("/admin/compos/$demoCompoId/setVoting/true").expectOk()

        // Read votekey table via sync API, pick a key, register it for john, cast a vote.
        // The key column stores "ticket:<8char>"; the /vote/register form accepts only the suffix.
        val voteKeys = readTable(syncToken, SyncedTable.VoteKeys)
        val freeKey = voteKeys.data.firstOrNull { (it["user_id"] as? JsonPrimitive)?.content == null }
            ?: voteKeys.data.firstOrNull()
            ?: error("[seed] no vote keys were generated")
        val rawKey = (freeKey["key"] as JsonPrimitive).content
        val freeKeyCode = rawKey.substringAfter("ticket:")
        println("[seed] Registering vote key $rawKey (code: $freeKeyCode) for john and casting a 3-point vote on the demo entry")

        val johnClient2 = InstanceClient("master-as-john-2", master.baseUrl)
        johnClient2.use { jc ->
            jc.login(johnName, johnPassword)
            jc.postMultipart("/vote/register", listOf("code" to freeKeyCode)).expectOk()
            jc.put("/vote/$entryId/3").expectOk()
        }

        printRowCounts(syncToken)

        return SeedResult(
            syncToken = syncToken,
            compoId = demoCompoId,
            entryId = entryId,
            johnUserId = johnUserId,
        )
    }

    private suspend fun readTable(token: String, table: SyncedTable): Table =
        master.getJsonWithToken("/sync/table/${table.name.lowercase()}", token)

    private suspend fun printRowCounts(token: String) {
        println("[seed] Row counts on master:")
        for (table in SyncedTable.entries) {
            val t = readTable(token, table)
            println("  ${table.name.padEnd(20)} -> ${t.data.size}")
        }
    }

    private fun randomBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        java.util.Random(0xC0FFEE).nextBytes(out)
        return out
    }
}
