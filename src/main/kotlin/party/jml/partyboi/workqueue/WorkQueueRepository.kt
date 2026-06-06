package party.jml.partyboi.workqueue

import arrow.core.Either
import arrow.core.flatten
import arrow.core.raise.either
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.json.Json
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
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
            SET state = 'Working', started_at = now(), attempts = attempts + 1
            WHERE id IN (
            	SELECT id
            	FROM task
            	WHERE state = 'Pending'
            	ORDER BY created_at
            	LIMIT 1
            	FOR UPDATE SKIP LOCKED
            )
            RETURNING *
        """.trimIndent()
            ).map(TaskRow.fromRow)
        ).flatten()
    }

    // Tasks left in 'Working' state were interrupted by a worker crash/restart (a running worker
    // always moves a task to a terminal state itself). Requeue the ones that still have attempts left
    // so they get retried, and give up on the rest — a task that keeps killing the worker must not be
    // requeued forever. Order matters: fail the exhausted ones before requeuing the remaining Working.
    suspend fun recoverStalledTasks(): AppResult<Unit> = db.use {
        either {
            exec(
                queryOf(
                    "UPDATE task SET state = 'Failed', finished_at = now() WHERE state = 'Working' AND attempts >= ?",
                    MAX_ATTEMPTS,
                )
            ).bind()
            exec(
                queryOf("UPDATE task SET state = 'Pending', started_at = NULL WHERE state = 'Working'")
            ).bind()
        }
    }

    suspend fun setState(id: UUID, state: TaskState) = db.use {
        val sql = if (state.isTerminal) {
            "UPDATE task SET state = ?, finished_at = now() WHERE id = ?"
        } else {
            "UPDATE task SET state = ? WHERE id = ?"
        }
        updateOne(queryOf(sql, state, id))
    }

    suspend fun list(limit: Int = 200): AppResult<List<TaskRow>> = db.use {
        either {
            many(
                queryOf("SELECT * FROM task ORDER BY created_at DESC LIMIT ?", limit)
                    .map(TaskRow.fromRow)
            ).bind().map { it.bind() }
        }
    }

    suspend fun getById(id: UUID): AppResult<TaskRow> = db.use {
        one(queryOf("SELECT * FROM task WHERE id = ?", id).map(TaskRow.fromRow)).flatten()
    }

    companion object {
        // How many times a task may be claimed before a crash-interrupted run is given up on.
        const val MAX_ATTEMPTS = 3
    }
}

data class TaskRow(
    val id: UUID,
    val task: Task,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val state: TaskState,
    val attempts: Int,
) {
    companion object {
        val fromRow: (Row) -> AppResult<TaskRow> = { row ->
            Either.catch {
                TaskRow(
                    id = row.uuid("id"),
                    task = Json.decodeFromString(row.string("task")),
                    createdAt = row.instant("created_at").toKotlinInstant(),
                    startedAt = row.instantOrNull("started_at")?.toKotlinInstant(),
                    finishedAt = row.instantOrNull("finished_at")?.toKotlinInstant(),
                    state = TaskState.valueOf(row.string("state")),
                    attempts = row.int("attempts"),
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
    Discarded;

    val isTerminal: Boolean
        get() = this == Success || this == Failed || this == Discarded
}