package party.jml.partyboi.screen

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import arrow.core.toNonEmptyListOrNone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.*
import party.jml.partyboi.data.DbBasicMappers.asBoolean
import party.jml.partyboi.data.Numbers.positiveInt

class ScreenRepository(private val app: AppServices) {
    val db = app.db

    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS screen (
                id SERIAL PRIMARY KEY,
                collection text NOT NULL,
                type text NOT NULL,
                content jsonb NOT NULL,
                enabled boolean NOT NULL DEFAULT true,
                run_order integer NOT NULL DEFAULT 0
            )
        """)
    }

    fun adHocExists(tx: TransactionalSession?) = db.use(tx) {
        it.one(queryOf("SELECT count(*) FROM screen WHERE collection = 'adhoc'").map(asBoolean))
    }

    fun getAdHoc(): Either<AppError, Option<ScreenRow>> = db.use {
        it.option(queryOf("SELECT * FROM screen WHERE collection = ?", "adhoc").map(ScreenRow.fromRow))
    }

    fun getCollection(name: String): Either<AppError, List<ScreenRow>> = db.use {
        it.many(queryOf("SELECT * FROM screen WHERE collection = ? ORDER BY run_order, id", name).map(ScreenRow.fromRow))
    }

    inline fun <reified A : Screen<A>> setAdHoc(screen: A): Either<AppError, ScreenRow> = db.transaction { either {
        val (type, content) = getTypeAndJson(screen)
        val query = if (adHocExists(it).bind()) {
            "UPDATE screen SET type = ?, content = ?::jsonb WHERE collection = 'adhoc' RETURNING *"
        } else {
            "INSERT INTO screen(collection, type, content) VALUES('adhoc', ?, ?::jsonb) RETURNING *"
        }
        it.one(queryOf(query, type, content).map(ScreenRow.fromRow)).bind()
    } }

    inline fun <reified A : Screen<A>> add(collection: String, screen: A): Either<AppError, ScreenRow> = db.use {
        val (type, content) = getTypeAndJson(screen)
        it.one(queryOf(
            "INSERT INTO screen(collection, type, content, enabled) VALUES(?, ?, ?::jsonb, false) RETURNING *",
            collection,
            type,
            content
        ).map(ScreenRow.fromRow))
    }

    inline fun <reified A : Screen<A>> update (id: Int, screen: A): Either<AppError, ScreenRow> = db.use {
        val (type, content) = getTypeAndJson(screen)
        it.one(queryOf("UPDATE screen SET type = ?, content = ?::jsonb WHERE id = ? RETURNING *", type, content, id).map(ScreenRow.fromRow))
    }

    fun getFirst(collection: String): Either<AppError, ScreenRow> = db.use {
        it.one(queryOf("SELECT * FROM screen WHERE collection = ? ORDER BY run_order, id LIMIT 1", collection).map(ScreenRow.fromRow))
    }

    fun getNext(collection: String, currentId: Int): Either<AppError, ScreenRow> = either {
        val screens = getCollection(collection).bind()
        val index = positiveInt(screens.indexOfFirst { it.id == currentId })
            .toEither { DatabaseError("$currentId not in collection $collection") }
            .bind()
        (screens.slice((index + 1)..<(screens.size)) + screens.slice(0..index))
            .filter { it.enabled }
            .toNonEmptyListOrNone()
            .toEither { DatabaseError("No enabled screens in collection $collection") }
            .map { it.first() }
            .bind()
    }

    inline fun <reified A : Screen<A>> getTypeAndJson(screen: A) = Pair(
        screen.javaClass.name,
        Json.encodeToString(screen)
    )

    fun setVisible(id: Int, visible: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("UPDATE screen SET enabled = ? WHERE id = ?", visible, id))
    }
}

data class ScreenRow(
    val id: Int,
    val collection: String,
    val type: String,
    val content: String,
    val enabled: Boolean,
    val runOrder: Int,
) {
    fun getScreen(): Screen<*> =
        when(type) {
            TextScreen::class.qualifiedName -> Json.decodeFromString<TextScreen>(content)
            else -> TODO("JSON decoding not implemented for $type")
        }

   companion object {
       val fromRow: (Row) -> ScreenRow = { row ->
           ScreenRow(
               id = row.int("id"),
               collection = row.string("collection"),
               type = row.string("type"),
               content = row.string("content"),
               enabled = row.boolean("enabled"),
               runOrder = row.int("run_order"),
           )
       }
   }
}