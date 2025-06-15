package party.jml.partyboi.system

import arrow.core.Either
import arrow.core.toOption
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.randomShortId
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import java.util.concurrent.atomic.AtomicInteger

class ErrorRepository(private val app: AppServices) {
    private val db = app.db
    private val errorCounter = AtomicInteger()

    fun errorCount(): Int = errorCounter.get()

    fun resetErrorCounter() {
        errorCounter.set(0)
    }

    inline fun <reified T> saveSafely(error: Throwable, context: T? = null): String? {
        val key = randomShortId()
        val jsonContext = try {
            context?.let { Json.encodeToString(it) }
        } catch (t: Throwable) {
            Json.encodeToString("Could not encode context: ${t.message}")
        }
        return save(key, error, jsonContext).toOption().map { key }.getOrNull()
    }

    fun save(key: String, error: Throwable, context: String? = null): Either<AppError, Unit> = db.use {
        it.exec(
            queryOf(
                """INSERT INTO error ("key", "message", "trace", "context") VALUES (?, ?, ?, ?::jsonb)""".trimIndent(),
                key,
                error.message,
                error.stackTrace.joinToString("\n"),
                context
            )
        ).onRight {
            errorCounter.incrementAndGet()
        }
    }

    fun getError(id: Int): Either<AppError, ErrorRow> = db.use {
        it.one(queryOf("SELECT * FROM error WHERE id = ?", id).map(ErrorRow.fromRow))
    }

    fun getErrors(limit: Int, pageIndex: Int): Either<AppError, List<ErrorRow>> = db.use {
        it.many(
            queryOf(
                "SELECT * FROM error ORDER BY time DESC LIMIT ? OFFSET ?",
                limit,
                limit * pageIndex,
            ).map(ErrorRow.fromRow)
        )
    }
}

data class ErrorRow(
    val id: Int,
    val key: String,
    val message: String,
    val trace: String?,
    val context: String?,
    val time: Instant,
) {
    companion object {
        val fromRow: (Row) -> ErrorRow = { row ->
            ErrorRow(
                id = row.int("id"),
                key = row.string("key"),
                message = row.string("message"),
                trace = row.string("trace"),
                context = row.stringOrNull("context"),
                time = row.instant("time").toKotlinInstant(),
            )
        }
    }
}