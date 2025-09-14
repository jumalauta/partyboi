package party.jml.partyboi.data

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.option
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.encodeToStringSafe

class PropertyRepository(app: AppServices) : Service(app) {
    private val db = app.db

    inline fun <reified T> createPersistentCachedValue(key: String, defaultValue: T): PersistentCachedValue<T> =
        PersistentCachedValue(
            fetchValue = {
                either {
                    get(key)
                        .bind()
                        .flatMap { Option.catch { Json.decodeFromString<T>(it.json) } }
                        .getOrElse { defaultValue }
                }
            },
            storeValue = { value ->
                either {
                    val json = Json.encodeToStringSafe(value)
                    store(key, json.bind()).onRight {
                        app.signals.emit(Signal.propertyUpdated(key))
                    }.bind()
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
