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
 * Drives syncDown and syncUp between the master and remote and verifies the result of each
 * direction. Polls the admin /sync log table to detect completion of a sync cycle. Both
 * directions use the master's bearer token: syncDown puts master's hash into the remote
 * property table, and the harness never overwrites it afterward, so master's plaintext token
 * authenticates against either side throughout the run.
 */
class Verifier(
    private val master: InstanceClient,
    private val remote: InstanceClient,
    private val masterInternalUrl: String, // e.g. "http://appserver-master:8123"
    private val masterToken: String,
    private val adminName: String = "admin",
    private val adminPassword: String = "password",
) {
    private val expectedDownloadEntries = SyncedTable.entries.map { "Download table '${it.tableName}'" }.toSet()
    private val expectedUploadEntries = SyncedTable.entries.map { "Upload table '${it.tableName}'" }.toSet()

    suspend fun configureRemote() {
        println("[verify] Logging into remote as $adminName")
        remote.login(adminName, adminPassword)
        println("[verify] Completing setup wizard on remote (admin routes redirect to /wizard until done)")
        remote.completeWizard()
        println("[verify] Configuring remote to point at $masterInternalUrl with the master's token")
        remote.postMultipart(
            "/sync/remote",
            listOf("address" to masterInternalUrl, "apiToken" to masterToken),
        ).expectOk()
    }

    suspend fun syncDown() {
        println("[verify] Triggering /sync/download on remote")
        remote.get("/sync/download").expectOk()
        waitForSyncCompletion("download", expectedDownloadEntries)

        val report = compareSubset(source = master, target = remote, direction = "syncDown")
        report.summarize("syncDown")
        if (!report.isPassing) throw Failure("syncDown verification failed")
    }

    suspend fun syncUp() {
        println("[verify] Triggering /sync/upload on remote")
        remote.get("/sync/upload").expectOk()
        waitForSyncCompletion("upload", expectedUploadEntries)

        val report = compareSubset(source = remote, target = master, direction = "syncUp")
        report.summarize("syncUp")
        if (!report.isPassing) throw Failure("syncUp verification failed")
    }

    private suspend fun waitForSyncCompletion(
        direction: String,
        expectedTableEntries: Set<String>,
        timeoutMs: Long = 180_000,
    ) {
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
                throw Failure("[$direction] sync log contains errors: ${errors.map { "${it.description} → ${it.errorText}" }}")
            }

            val summary = "by status=$totalsByStatus, $direction tables OK=${tablesOk.size}/${expectedTableEntries.size}, running=$running"
            if (summary != lastSummary) {
                println("[verify] poll($direction): $summary")
                lastSummary = summary
            }

            if (tablesOk == expectedTableEntries && running == 0) {
                println("[verify] $direction cycle complete")
                return
            }
            if (System.currentTimeMillis() >= deadline) {
                throw Failure("[$direction] timeout. $summary. Recent log:\n${entries.takeLast(20).joinToString("\n") { "  ${it.status} - ${it.description}" }}")
            }
            delay(1500)
        }
    }

    private data class LogRow(val status: String, val description: String, val errorText: String?)

    private suspend fun parseSyncLog(): List<LogRow> {
        val html = remote.getHtml("/sync")
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr[class^=sync-status-]")
        val out = mutableListOf<LogRow>()
        var lastStatus = ""
        var lastDescription = ""
        for (row in rows) {
            val cls = row.attr("class")
            val cells = row.select("td")
            if (cls.contains("sync-status-error") && cells.size == 1) {
                // Continuation row carrying the error message body.
                if (out.isNotEmpty()) {
                    val prev = out.removeAt(out.size - 1)
                    out += prev.copy(errorText = cells[0].text().trim())
                }
                continue
            }
            if (cells.size < 2) continue
            lastStatus = cells[0].text().trim()
            lastDescription = cells[1].text().trim()
            out += LogRow(lastStatus, lastDescription, null)
        }
        return out
    }

    /**
     * For every row in `source`, find a row in `target` with the same primary key and assert
     * field-level equality. Presence-only on Users (sync's name-collision resolver mutates
     * names — see the production-bug note in the harness commit) and Properties (`apiKey` is
     * overwritten in either direction).
     */
    private suspend fun compareSubset(
        source: InstanceClient,
        target: InstanceClient,
        direction: String,
    ): VerifyReport {
        val tableMismatches = mutableListOf<String>()
        for (table in SyncedTable.entries) {
            val s: Table = source.getJsonWithToken("/sync/table/${table.name.lowercase()}", masterToken)
            val t: Table = target.getJsonWithToken("/sync/table/${table.name.lowercase()}", masterToken)
            val targetById = t.data.mapNotNull { row -> rowKey(row)?.let { it to row } }.toMap()
            for (sRow in s.data) {
                val key = rowKey(sRow)
                if (key == null) {
                    tableMismatches += "[$direction/${table.name}] source row has no recognizable primary key: $sRow"
                    continue
                }
                val tRow = targetById[key]
                if (tRow == null) {
                    tableMismatches += "[$direction/${table.name}] source row id=$key missing on target"
                    continue
                }
                if (table == SyncedTable.Users || table == SyncedTable.Properties) continue
                if (sRow != tRow) {
                    val diffKeys = sRow.keys.filter { sRow[it] != tRow[it] }
                    tableMismatches += "[$direction/${table.name}] row id=$key differs in fields: ${diffKeys.joinToString()}\n    source: ${sRow.filterKeys { it in diffKeys }}\n    target: ${tRow.filterKeys { it in diffKeys }}"
                }
            }
        }
        val fileMismatches = compareFiles(source, target, direction)
        return VerifyReport(tableMismatches, fileMismatches)
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

    private suspend fun compareFiles(
        source: InstanceClient,
        target: InstanceClient,
        direction: String,
    ): List<String> {
        val mismatches = mutableListOf<String>()
        val sourceFiles: Table = source.getJsonWithToken("/sync/table/files", masterToken)
        for (row in sourceFiles.data) {
            val id = (row["id"] as? JsonPrimitive)?.content ?: continue
            val size = (row["size"] as? JsonPrimitive)?.content?.toLongOrNull()
            val checksum = (row["checksum"] as? JsonPrimitive)?.content

            val sourceResp = source.get("/sync/file/$id") { bearerAuth(masterToken) }
            if (sourceResp.status != HttpStatusCode.OK) {
                mismatches += "[$direction/file $id] source returned ${sourceResp.status}"
                continue
            }
            val sourceBytes = sourceResp.bodyAsBytes()

            val targetResp = target.get("/sync/file/$id") { bearerAuth(masterToken) }
            if (targetResp.status != HttpStatusCode.OK) {
                mismatches += "[$direction/file $id] target returned ${targetResp.status}"
                continue
            }
            val targetBytes = targetResp.bodyAsBytes()

            if (sourceBytes.size.toLong() != targetBytes.size.toLong()) {
                mismatches += "[$direction/file $id] size differs: source=${sourceBytes.size} target=${targetBytes.size}"
                continue
            }
            if (size != null && sourceBytes.size.toLong() != size) {
                mismatches += "[$direction/file $id] source bytes ${sourceBytes.size} != recorded size $size"
            }
            val sourceMd5 = md5Hex(sourceBytes)
            val targetMd5 = md5Hex(targetBytes)
            if (sourceMd5 != targetMd5) {
                mismatches += "[$direction/file $id] md5 differs: source=$sourceMd5 target=$targetMd5"
            } else if (checksum != null && checksum != sourceMd5) {
                mismatches += "[$direction/file $id] source md5 $sourceMd5 != recorded checksum $checksum"
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

    fun summarize(direction: String) {
        println()
        println("======================================================================")
        if (isPassing) {
            println("VERIFY ($direction): PASS — all ${SyncedTable.entries.size} tables and files reconciled")
        } else {
            println("VERIFY ($direction): FAIL")
            tableMismatches.forEach { println("  $it") }
            fileMismatches.forEach { println("  $it") }
        }
        println("======================================================================")
    }
}
