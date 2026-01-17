package party.jml.partyboi.sync

import arrow.core.raise.either
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.db.many
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.system.AppResult

class DbSyncService(app: AppServices) : Service(app) {
    private val db = app.db

    suspend fun getTable(tableName: String): AppResult<Table> =
        either {
            val columns = getColumns(tableName).bind()
            val data = db.use {
                it.many(queryOf("SELECT * FROM $tableName").map { row ->
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

    suspend fun getColumns(tableName: String): AppResult<Map<String, String>> =
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
}

@Serializable
data class Table(
    val table: String,
    val time: Instant,
    val data: List<Map<String, JsonElement>>
)