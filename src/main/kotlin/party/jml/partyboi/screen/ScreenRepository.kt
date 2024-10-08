package party.jml.partyboi.screen

import arrow.core.raise.either
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.DbBasicMappers.asBoolean
import party.jml.partyboi.data.exec
import party.jml.partyboi.data.one
import party.jml.partyboi.data.option

class ScreenRepository(private val app: AppServices) {
    private val db = app.db

    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS screen (
                id SERIAL PRIMARY KEY,
                collection text NOT NULL,
                type text NOT NULL,
                content jsonb NOT NULL,
                enabled boolean NOT NULL DEFAULT true
            )
        """)
    }

    fun adHocExists(tx: TransactionalSession?) = db.use(tx) {
        it.one(queryOf("SELECT count(*) FROM screen WHERE collection = 'adhoc'").map(asBoolean))
    }

    fun getAdHoc() = db.use {
        it.option(queryOf("SELECT * FROM screen WHERE collection = ?", "adhoc").map(ScreenRow.fromRow))
            .map { it.map { it.content } }
    }

    fun addAdHoc(screen: TextScreen) = db.transaction { either {
        val content = Json.encodeToString(screen)
        val type = screen.javaClass.simpleName
        if (adHocExists(it).bind()) {
            it.exec(queryOf("UPDATE screen SET type = ?, content = ?::jsonb WHERE collection = 'adhoc'", type, content)).bind()
        } else {
            it.exec(queryOf("INSERT INTO screen(collection, type, content) VALUES('adhoc', ?, ?::jsonb)", type, content)).bind()
        }
    } }
}

data class ScreenRow <A: Screen> (
    val id: Int,
    val collection: String,
    val type: String,
    val content: A,
    val enabled: Boolean,
) {
   companion object {
       val fromRow: (Row) -> ScreenRow<Screen> = { row ->
           val type = row.string("type")
           val content = row.string("content")
           ScreenRow(
               id = row.int("id"),
               collection = row.string("collection"),
               type = type,
               content = when(type) {
                   TextScreen::class.simpleName -> Json.decodeFromString<TextScreen>(content)
                   else -> TODO("JSON decoding not implemented for $type")
               },
               enabled = row.boolean("enabled"),
           )
       }
   }
}