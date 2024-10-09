package party.jml.partyboi.screen

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
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

    fun getAdHoc(): Either<AppError, Option<ScreenRow<Screen<*>>>> = db.use {
        it.option(queryOf("SELECT * FROM screen WHERE collection = ?", "adhoc").map(ScreenRow.fromRow))
    }

    fun getCollection(name: String): Either<AppError, List<ScreenRow<Screen<*>>>> = db.use {
        it.many(queryOf("SELECT * FROM screen WHERE collection = ? ORDER BY run_order, id", name).map(ScreenRow.fromRow))
    }

    inline fun <reified A : Screen<A>> setAdHoc(screen: A): Either<AppError, ScreenRow<Screen<*>>> = db.transaction { either {
        val (type, content) = getTypeAndJson(screen)
        it.one(queryOf(
            if (adHocExists(it).bind()) {
                "UPDATE screen SET type = ?, content = ?::jsonb WHERE collection = 'adhoc' RETURNING *"
            } else {
                "INSERT INTO screen(collection, type, content) VALUES('adhoc', ?, ?::jsonb)"
            },
            type,
            content,
        ).map(ScreenRow.fromRow)).bind()
    } }

    inline fun <reified A : Screen<A>> add(collection: String, screen: A): Either<AppError, ScreenRow<Screen<*>>> = db.use {
        val (type, content) = getTypeAndJson(screen)
        it.one(queryOf(
            "INSERT INTO screen(collection, type, content, enabled) VALUES(?, ?, ?::jsonb, false) RETURNING *",
            collection,
            type,
            content
        ).map(ScreenRow.fromRow))
    }

    inline fun <reified A : Screen<A>> update (id: Int, screen: A): Either<AppError, ScreenRow<Screen<*>>> = db.use {
        val (type, content) = getTypeAndJson(screen)
        it.one(queryOf("UPDATE screen SET type = ?, content = ?::jsonb WHERE id = ? RETURNING *", type, content, id).map(ScreenRow.fromRow))
    }

    fun getFirst(collection: String): Either<AppError, ScreenRow<Screen<*>>> = db.use {
        it.one(queryOf("SELECT * FROM screen WHERE collection = ? ORDER BY run_order, id LIMIT 1", collection).map(ScreenRow.fromRow))
    }

    fun getNext(collection: String, currentId: Int): Either<AppError, ScreenRow<Screen<*>>> = either {
        val screens = getCollection(collection).bind()
        val index = positiveInt(screens.indexOfFirst { it.id == currentId })
            .toEither { DatabaseError("$currentId not in collection $collection") }
            .bind()
        val nextIndex = (index + 1) % screens.size
        screens[nextIndex]
    }

    inline fun <reified A : Screen<A>> getTypeAndJson(screen: A) = Pair(
        screen.javaClass.name,
        Json.encodeToString(screen)
    )
}

data class ScreenRow <A: Screen<*>> (
    val id: Int,
    val collection: String,
    val type: String,
    val content: A,
    val enabled: Boolean,
    val runOrder: Int,
) {
   companion object {
       val fromRow: (Row) -> ScreenRow<Screen<*>> = { row ->
           val type = row.string("type")
           val content = row.string("content")
           ScreenRow(
               id = row.int("id"),
               collection = row.string("collection"),
               type = type,
               content = when(type) {
                   TextScreen::class.qualifiedName -> Json.decodeFromString<TextScreen>(content)
                   else -> TODO("JSON decoding not implemented for $type")
               },
               enabled = row.boolean("enabled"),
               runOrder = row.int("run_order"),
           )
       }
   }
}