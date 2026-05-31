package party.jml.partyboi.syncharness

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import party.jml.partyboi.sync.SyncedTable
import party.jml.partyboi.sync.Table
import java.security.MessageDigest

class Failure(message: String) : RuntimeException(message)

/**
 * Configures the remote to point at the master, triggers syncDown, polls for completion
 * by scraping the admin /sync log table, then compares table contents + file bytes.
 *
 * Polling can't go through `/sync/missing-files` with bearer auth because the property
 * table is synced and the remote's expectedApiKey is a CachedValue with a 1-hour TTL. A
 * fresh sync writes a new hash to the DB but does not invalidate the cache, so bearer
 * auth either NPEs (pre-sync, cache=null) or rejects valid tokens (cache stale). After
 * the sync completes we POST /sync/new-token on the remote — that path updates the cache
 * and the DB atomically — and use that token for the verification GETs.
 */
class Verifier(
    private val master: InstanceClient,
    private val remote: InstanceClient,
    private val masterInternalUrl: String, // e.g. "http://appserver-master:8123"
    private val masterToken: String,
    private val adminName: String = "admin",
    private val adminPassword: String = "password",
) {
    suspend fun run(): VerifyReport {
        println("[verify] Logging into remote as $adminName")
        remote.login(adminName, adminPassword)

        println("[verify] Configuring remote to point at $masterInternalUrl with the master's token")
        remote.postMultipart(
            "/sync/remote",
            listOf("address" to masterInternalUrl, "apiToken" to masterToken),
        ).expectOk()

        val expectedTableEntries = SyncedTable.entries.map { "Download table '${it.tableName}'" }.toSet()

        println("[verify] First syncDown")
        runOneSyncCycle(expectedTableEntries)

        // Refresh the remote's bearer-token cache so the verifier can use the sync HTTP API.
        val remoteToken = remote.generateSyncToken()

        val tableMismatches = compareTables(remoteToken)
        val fileMismatches = compareFiles(remoteToken)
        val report = VerifyReport(tableMismatches = tableMismatches, fileMismatches = fileMismatches)
        report.summarize()
        if (!report.isPassing) {
            throw Failure("Sync verification failed — see report above")
        }

        println("[verify] Second syncDown for idempotency smoke check")
        runOneSyncCycle(expectedTableEntries)
        // The 2nd sync overwrote the property table; the cached token still works (cache
        // was set when we generated remoteToken; CachedValue doesn't reload from DB unless
        // refresh() is called or the TTL expires). That's what we want — we keep using
        // remoteToken for the post-sync comparison.
        val idempotentMismatches = compareTables(remoteToken)
        if (idempotentMismatches.isNotEmpty()) {
            throw Failure("Idempotency check failed: tables changed after a second syncDown:\n${idempotentMismatches.joinToString("\n")}")
        }
        println("[verify] Idempotency OK")

        return report
    }

    private suspend fun runOneSyncCycle(expectedTableEntries: Set<String>, timeoutMs: Long = 180_000) {
        println("[verify] Triggering /sync/download on remote (launches background syncDown)")
        remote.get("/sync/download").expectOk()
        waitForSyncCompletion(expectedTableEntries, timeoutMs)
    }

    private suspend fun waitForSyncCompletion(expectedTableEntries: Set<String>, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSummary = ""
        while (true) {
            val entries = parseSyncLog()
            val totalsByStatus = entries.groupingBy { it.status }.eachCount()
            val tablesOk = entries.filter { it.status == "OK" && it.description in expectedTableEntries }
                .map { it.description }.toSet()
            val errors = entries.filter { it.status == "Error" }
            val running = entries.count { it.status == "Running" }

            if (errors.isNotEmpty()) {
                throw Failure("Sync log contains errors: ${errors.map { it.description }}")
            }

            val summary = "by status=$totalsByStatus, tables OK=${tablesOk.size}/${expectedTableEntries.size}, running=$running"
            if (summary != lastSummary) {
                println("[verify] poll: $summary")
                lastSummary = summary
            }

            // Done when: every expected table has an OK entry AND nothing is still running.
            // Files are downloaded after tables; the `running == 0` condition covers them.
            if (tablesOk == expectedTableEntries && running == 0) {
                println("[verify] sync cycle complete")
                return
            }
            if (System.currentTimeMillis() >= deadline) {
                throw Failure("Timeout waiting for sync to complete. $summary. Recent log:\n${entries.takeLast(20).joinToString("\n") { "  ${it.status} - ${it.description}" }}")
            }
            delay(1500)
        }
    }

    private data class LogRow(val status: String, val description: String)

    private suspend fun parseSyncLog(): List<LogRow> {
        val html = remote.getHtml("/sync")
        val doc = Jsoup.parse(html)
        // The sync log table is the last <table> on the page; rows have class sync-status-{ok,error,running}.
        val rows = doc.select("tr[class^=sync-status-]")
        return rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 2) return@mapNotNull null
            val status = cells[0].text().trim()
            val description = cells[1].text().trim()
            LogRow(status = status, description = description)
        }
    }

    /**
     * For each row on master, find a row on remote with the matching primary key and assert
     * the content matches. Extra rows on remote are allowed (remote may have its own admin,
     * its own messages, etc.). For the Users table we only verify presence by id, because
     * the sync's name-collision resolver intentionally mutates user names. For Properties
     * we also only verify presence — sync overwrites the remote's apiKey hash.
     */
    private suspend fun compareTables(remoteToken: String): List<String> {
        val mismatches = mutableListOf<String>()
        for (table in SyncedTable.entries) {
            val m: Table = master.getJsonWithToken("/sync/table/${table.name.lowercase()}", masterToken)
            val r: Table = remote.getJsonWithToken("/sync/table/${table.name.lowercase()}", remoteToken)
            val remoteById = r.data.mapNotNull { row -> rowKey(row)?.let { it to row } }.toMap()
            for (mRow in m.data) {
                val key = rowKey(mRow)
                if (key == null) {
                    mismatches += "[${table.name}] row has no recognizable primary key: $mRow"
                    continue
                }
                val rRow = remoteById[key]
                if (rRow == null) {
                    mismatches += "[${table.name}] master row id=$key not present on remote"
                    continue
                }
                if (table == SyncedTable.Users || table == SyncedTable.Properties) {
                    // Presence is sufficient: name collisions on Users are intentionally
                    // resolved by sync, and the verifier's own /sync/new-token call rewrites
                    // the apiKey property hash post-sync.
                    continue
                }
                if (mRow != rRow) {
                    val diffKeys = mRow.keys.filter { mRow[it] != rRow[it] }
                    mismatches += "[${table.name}] row id=$key differs in fields: ${diffKeys.joinToString()}\n    master: ${mRow.filterKeys { it in diffKeys }}\n    remote: ${rRow.filterKeys { it in diffKeys }}"
                }
            }
        }
        return mismatches
    }

    private fun rowKey(row: Map<String, JsonElement>): String? {
        for (key in listOf("id", "key")) {
            (row[key] as? JsonPrimitive)?.content?.let { return "$key=$it" }
        }
        val entryId = (row["entry_id"] as? JsonPrimitive)?.content
        val fileId = (row["file_id"] as? JsonPrimitive)?.content
        val userId = (row["user_id"] as? JsonPrimitive)?.content
        val slideSet = (row["set_name"] as? JsonPrimitive)?.content
        return when {
            entryId != null && fileId != null -> "entry_id=$entryId|file_id=$fileId"
            entryId != null && userId != null -> "entry_id=$entryId|user_id=$userId"
            slideSet != null -> "set=$slideSet"
            entryId != null -> "entry_id=$entryId"
            else -> null
        }
    }

    private suspend fun compareFiles(remoteToken: String): List<String> {
        val mismatches = mutableListOf<String>()
        val masterFiles: Table = master.getJsonWithToken("/sync/table/files", masterToken)
        for (row in masterFiles.data) {
            val id = (row["id"] as? JsonPrimitive)?.content ?: continue
            val size = (row["size"] as? JsonPrimitive)?.content?.toLongOrNull()
            val checksum = (row["checksum"] as? JsonPrimitive)?.content

            val masterResp = master.get("/sync/file/$id") { bearerAuth(masterToken) }
            if (masterResp.status != HttpStatusCode.OK) {
                mismatches += "[file $id] master returned ${masterResp.status}; cannot compare"
                continue
            }
            val masterBytes = masterResp.bodyAsBytes()

            val remoteResp = remote.get("/sync/file/$id") { bearerAuth(remoteToken) }
            if (remoteResp.status != HttpStatusCode.OK) {
                mismatches += "[file $id] remote returned ${remoteResp.status}"
                continue
            }
            val remoteBytes = remoteResp.bodyAsBytes()

            if (masterBytes.size.toLong() != remoteBytes.size.toLong()) {
                mismatches += "[file $id] size differs: master=${masterBytes.size} remote=${remoteBytes.size}"
                continue
            }
            if (size != null && masterBytes.size.toLong() != size) {
                mismatches += "[file $id] master file size ${masterBytes.size} != recorded size $size"
            }
            val masterMd5 = md5Hex(masterBytes)
            val remoteMd5 = md5Hex(remoteBytes)
            if (masterMd5 != remoteMd5) {
                mismatches += "[file $id] md5 differs: master=$masterMd5 remote=$remoteMd5"
            } else if (checksum != null && checksum != masterMd5) {
                mismatches += "[file $id] master md5 $masterMd5 != recorded checksum $checksum"
            }
        }
        return mismatches
    }

    private fun md5Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class VerifyReport(
    val tableMismatches: List<String>,
    val fileMismatches: List<String>,
) {
    val isPassing: Boolean get() = tableMismatches.isEmpty() && fileMismatches.isEmpty()

    fun summarize() {
        println()
        println("======================================================================")
        if (isPassing) {
            println("VERIFY: PASS — all ${SyncedTable.entries.size} tables and files match between master and remote")
        } else {
            println("VERIFY: FAIL")
            tableMismatches.forEach { println("  $it") }
            fileMismatches.forEach { println("  $it") }
        }
        println("======================================================================")
    }
}
