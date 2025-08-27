package party.jml.partyboi.data

import arrow.core.Option
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.option
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult

class PropertyRepository(app: AppServices) : Service(app) {
    private val db = app.db

    inline fun <reified T> createPersistentCachedValue(key: String, defaultValue: T): PersistentCachedValue<T> =
        PersistentCachedValue(
            fetchValue = {
                either {
                    get(key)
                        .bind()
                        .fold(
                            { defaultValue },
                            { Json.decodeFromString<T>(it.json) }
                        )
                }
            },
            storeValue = { value ->
                store(key, Json.encodeToString(value)).onRight {
                    app.signals.emit(Signal.propertyUpdated(key))
                }
            }
        )

    suspend fun get(key: String): AppResult<Option<PropertyRow>> = db.use {
        it.option(queryOf("SELECT * FROM property WHERE key = ?", key).map(PropertyRow.fromRow))
    }

    suspend fun getAll(): AppResult<List<PropertyRow>> = db.use {
        it.many(queryOf("SELECT * FROM property").map(PropertyRow.fromRow))
    }

    suspend inline fun <reified A> getOrElse(key: String, value: A): AppResult<PropertyRow> =
        get(key).map { it.fold({ PropertyRow(key, Json.encodeToString(value)) }, { it }) }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.properties.size} properties")
        data.properties.map {
            tx.exec(
                queryOf(
                    "INSERT INTO property (key, value) VALUES (?, ?::jsonb)",
                    it.key,
                    it.json,
                )
            )
        }.bindAll()
    }

    suspend fun store(key: String, jsonValue: String) =
        db.use {
            it.exec(
                queryOf(
                    """
                    INSERT INTO property (key, value)
                    VALUES (?, ?::jsonb)
                    ON CONFLICT (key) DO UPDATE SET
                        value = EXCLUDED.value::jsonb
                """.trimIndent(),
                    key,
                    jsonValue
                )
            )
        }
}

@Serializable
data class PropertyRow(
    val key: String,
    val json: String,
) {
    companion object {
        val fromRow: (Row) -> PropertyRow = { row ->
            PropertyRow(
                key = row.string("key"),
                json = row.string("value"),
            )
        }
    }
}
