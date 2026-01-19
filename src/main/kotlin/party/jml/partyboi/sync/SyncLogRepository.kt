package party.jml.partyboi.sync

import arrow.core.flatten
import arrow.core.raise.either
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.db.updateOne
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.system.AppResult
import java.util.*

class SyncLogRepository(app: AppServices) : Service(app) {
    private val db = app.db

    suspend fun getAll(): AppResult<List<SyncLogEntry>> =
        db.use {
            it.many(
                queryOf("SELECT * FROM synclog ORDER BY start_time ASC").map(SyncLogEntry.fromRow)
            )
        }

    suspend fun <T> use(id: SyncLogId, block: suspend () -> AppResult<T>): AppResult<T> =
        AppResult.catch {
            either {
                startEntry(id).bind()
                block().bind()
            }.onRight {
                completeEntry(id)
            }.onLeft {
                failEntry(id, it.message)
            }
        }.mapLeft {
            failEntry(id, it.toString())
            InternalServerError(it)
        }.flatten()

    suspend fun startEntry(id: SyncLogId) =
        db.use {
            it.exec(
                queryOf(
                    """
                    INSERT INTO synclog (id, description, start_time)
                    VALUES (?, ?, now())
                    ON CONFLICT (id) DO UPDATE SET
                        id = EXCLUDED.id,
                        description = EXCLUDED.description,
                        start_time = EXCLUDED.start_time,
                        end_time = null,
                        success = null,
                        error = null
                """.trimIndent(),
                    id.toString(),
                    id.description()
                )
            )
        }

    suspend fun completeEntry(id: SyncLogId) =
        db.use {
            it.updateOne(
                queryOf(
                    """
                        UPDATE synclog SET
                            end_time = now(),
                            success = true
                        WHERE id = ?
                          AND success is null
                    """.trimIndent(),
                    id.toString()
                )
            )
        }

    suspend fun failEntry(id: SyncLogId, error: String) =
        db.use {
            it.updateOne(
                queryOf(
                    """
                        UPDATE synclog SET
                            end_time = now(),
                            success = false,
                            error = ?
                        WHERE id = ?
                          AND success is null
                    """.trimIndent(),
                    error,
                    id.toString()
                )
            )
        }

}

sealed interface SyncLogId {
    fun description(): String
}

data class TableSyncId(
    val tableName: String,
) : SyncLogId {
    override fun toString(): String = "table-$tableName"

    override fun description(): String = "Table '$tableName'"
}

data class FileSyncId(
    val file: FileDesc,
) : SyncLogId {
    override fun toString(): String = "file-${file.id}"

    override fun description(): String =
        "File '${file.originalFilename}' (${Filesize.humanFriendly(file.size)})"
}

data class SyncLogEntry(
    val id: String,
    val description: String,
    val startTime: Instant,
    val endTime: Instant?,
    val error: String?
) {
    val hasEnded: Boolean = endTime != null
    val isSuccess: Boolean = hasEnded && error == null

    companion object {
        val fromRow: (Row) -> SyncLogEntry = { row ->
            SyncLogEntry(
                id = row.string("id"),
                description = row.string("description"),
                startTime = row.instant("start_time").toKotlinInstant(),
                endTime = row.instantOrNull("end_time")?.toKotlinInstant(),
                error = row.stringOrNull("error")
            )
        }
    }
}