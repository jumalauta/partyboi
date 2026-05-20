package party.jml.partyboi.workqueue

import arrow.core.Either
import arrow.core.flatten
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.json.Json
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.db.updateOne
import party.jml.partyboi.system.AppResult
import java.util.*

class WorkQueueRepository(val app: AppServices) {
    private val db = app.db

    suspend fun add(task: Task): AppResult<TaskRow> = db.use {
        one(
            queryOf(
                "INSERT INTO task(task, state) VALUES (?::jsonb, ?) RETURNING *",
                Json.encodeToString(task),
                TaskState.Pending
            ).map(TaskRow.fromRow)
        ).flatten()
    }

    suspend fun takeNext(): AppResult<TaskRow> = db.use {
        one(
            queryOf(
                """
            UPDATE task
            SET state = 'Working'
            WHERE id IN (
            	SELECT id
            	FROM task
            	WHERE state = 'Pending'
            	ORDER BY created_at
            	LIMIT 1
            )
            RETURNING *
        """.trimIndent()
            ).map(TaskRow.fromRow)
        ).flatten()
    }

    suspend fun cancel(): AppResult<Unit> = db.use {
        exec(queryOf("UPDATE task SET state = 'Discarded' WHERE state = 'Working'"))
    }

    suspend fun setState(id: UUID, state: TaskState) = db.use {
        updateOne(queryOf("UPDATE task SET state = ? WHERE id = ?", state, id))
    }
}

data class TaskRow(
    val id: UUID,
    val task: Task,
    val createdAt: Instant,
    val finishedAt: Instant?,
    val state: TaskState
) {
    companion object {
        val fromRow: (Row) -> AppResult<TaskRow> = { row ->
            Either.catch {
                TaskRow(
                    id = row.uuid("id"),
                    task = Json.decodeFromString(row.string("task")),
                    createdAt = row.instant("created_at").toKotlinInstant(),
                    finishedAt = row.instantOrNull("finished_at")?.toKotlinInstant(),
                    state = TaskState.valueOf(row.string("state")),
                )
            }.mapLeft { InternalServerError(it) }
        }
    }
}

enum class TaskState {
    Pending,
    Working,
    Success,
    Failed,
    Discarded
}