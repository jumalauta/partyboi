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
import party.jml.partyboi.db.option
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.encodeToStringSafe

class PropertyRepository(app: AppServices) : Service(app) {
    private val db = app.db

    inline fun <reified T> createPersistentCachedValue(
        key: String,
        private: Boolean,
        defaultValue: T
    ): PersistentCachedValue<T> =
        PersistentCachedValue(
            fetchValue = {
                either {
                    get(key, private)
                        .bind()
                        .flatMap { Option.catch { Json.decodeFromString<T>(it.json) } }
                        .getOrElse { defaultValue }
                }
            },
            storeValue = { value ->
                either {
                    val json = Json.encodeToStringSafe(value)
                    store(key, private, json.bind()).onRight {
                        app.signals.emit(Signal.propertyUpdated(key))
                    }.bind()
                }
            }
        )

    suspend fun get(key: String, private: Boolean): AppResult<Option<PropertyRow>> = db.use {
        it.option(queryOf("SELECT * FROM ${table(private)} WHERE key = ?", key).map(PropertyRow.fromRow))
    }

    suspend fun store(key: String, private: Boolean, jsonValue: String) =
        db.use {
            it.exec(
                queryOf(
                    """
                    INSERT INTO ${table(private)} (key, value)
                    VALUES (?, ?::jsonb)
                    ON CONFLICT (key) DO UPDATE SET
                        value = EXCLUDED.value::jsonb
                """.trimIndent(),
                    key,
                    jsonValue
                )
            )
        }

    fun table(private: Boolean): String = if (private) "private_property" else "property"
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
