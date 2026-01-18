package party.jml.partyboi.sync

import arrow.core.*
import arrow.core.raise.either
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotliquery.Session
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.AppError
import party.jml.partyboi.db.many
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.db.updateAny
import party.jml.partyboi.system.AppResult

typealias ExceptionResolver = (Throwable?, Map<String, JsonElement>) -> Map<String, JsonElement>?

class DbSyncService(app: AppServices) : Service(app) {
    private val db = app.db

    suspend fun getTable(tableName: String): AppResult<Table> =
        either {
            val columns = getColumns(tableName).bind()
            val data = db.use {
                it.many(queryOf("SELECT * FROM $tableName ORDER BY ${columns.keys.first()}").map { row ->
                    columns.map { (colName, colType) ->
                        colName to when (colType) {
                            "boolean" -> JsonPrimitive(row.boolean(colName))
                            "integer", "numeric" -> JsonPrimitive(row.longOrNull(colName))
                            else -> JsonPrimitive(row.stringOrNull(colName))
                        }
                    }.toMap()
                })
            }.bind()

            Table(tableName, Clock.System.now(), data)
        }

    suspend fun putTable(table: Table, exceptionResolver: ExceptionResolver?) =
        either {
            val columns = getColumns(table.table).bind()
            val pkeyResolver = getPrimaryKeyConstraint(table.table).bind()

            db.use { session ->
                either {
                    table.data.map { row ->
                        putEntry(
                            session = session,
                            tableName = table.table,
                            columns = columns,
                            row = row,
                            conflictResolver = pkeyResolver,
                            exceptionResolver = exceptionResolver
                        )
                    }.bindAll()
                }
            }
        }.flatten()

    private fun putEntry(
        session: Session,
        tableName: String,
        columns: Map<String, String>,
        row: Map<String, JsonElement>,
        conflictResolver: ConflictResolveStrategy?,
        exceptionResolver: ExceptionResolver?
    ): AppResult<Int> = session.updateAny(
        queryOf(
            """
                        INSERT INTO ${tableName}
                        (${row.keys.joinToString(",")}) 
                        VALUES (${
                row.keys.joinToString(",") { key ->
                    when (columns[key]) {
                        "uuid" -> "?::uuid"
                        "timestamp with time zone" -> "?::timestamp"
                        "jsonb" -> "?::jsonb"
                        "ARRAY" -> "?::text[]"
                        else -> "?"
                    }
                }
            })
                        ${conflictResolver?.sql(columns.keys).orEmpty()}
                        """.trimIndent(),
            *row.values.map { it.toNative() }.toTypedArray()
        )
    ).fold({ error ->
        exceptionResolver?.let {
            exceptionResolver(error.throwable, row)?.let { newRow ->
                putEntry(session, tableName, columns, newRow, conflictResolver, exceptionResolver)
            }
        } ?: error.left()
    }, { it.right() })

    private suspend fun getColumns(tableName: String): AppResult<Map<String, String>> =
        db.use {
            it.many(
                queryOf(
                    "SELECT * FROM information_schema.columns WHERE table_name = ?",
                    tableName
                ).map { row ->
                    row.string("column_name") to row.string("data_type")
                }
            ).map { cols -> cols.toMap() }
        }

    private suspend fun getPrimaryKeyConstraint(tableName: String): Either<AppError, UpdateAllColumnsExcept?> =
        db.use {
            it.many(
                queryOf(
                    """
                    SELECT
                        tc.constraint_name,
                        kcu.column_name
                    FROM information_schema.key_column_usage kcu
                    JOIN information_schema.table_constraints tc
                      ON tc.constraint_name = kcu.constraint_name
                     AND tc.table_schema = kcu.table_schema
                    WHERE kcu.table_name = ?
                      AND tc.constraint_type = 'PRIMARY KEY'
                """.trimIndent(), tableName
                ).map { row -> row.string("constraint_name") to row.string("column_name") })
        }.map { result ->
            result.toNonEmptyListOrNull()?.let { r ->
                UpdateAllColumnsExcept(r.first().first, r.map { it.second }.toSet())
            }
        }
}

@Serializable
data class Table(
    val table: String,
    val time: Instant,
    val data: List<Map<String, JsonElement>>
)

fun JsonElement.toNative(): Any? =
    when (this) {
        is JsonNull -> null

        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> boolean
            longOrNull != null -> long
            doubleOrNull != null -> double
            else -> content
        }

        is JsonArray ->
            map { it.toNative() }

        is JsonObject ->
            mapValues { (_, v) -> v.toNative() }
    }

abstract class ConflictResolveStrategy {
    abstract val constraintName: String
    abstract fun sql(allColumns: Set<String>): String
}

abstract class UpdateColumns(override val constraintName: String) : ConflictResolveStrategy() {
    abstract fun columnsToUpdate(allColumns: Set<String>): Set<String>

    override fun sql(allColumns: Set<String>): String =
        "ON CONFLICT ON CONSTRAINT $constraintName DO UPDATE SET\n" +
                columnsToUpdate(allColumns).joinToString(",\n") { "$it = EXCLUDED.$it" }
}

class UpdateAllColumnsExcept(constraintName: String, val ignoredColumns: Set<String>) : UpdateColumns(constraintName) {
    override fun columnsToUpdate(allColumns: Set<String>): Set<String> = allColumns.minus(ignoredColumns)
}