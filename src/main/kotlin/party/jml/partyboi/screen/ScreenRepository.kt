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
                slide_set text NOT NULL,
                type text NOT NULL,
                content jsonb NOT NULL,
                visible boolean NOT NULL DEFAULT true,
                run_order integer NOT NULL DEFAULT 0
            )
        """)
    }

    fun adHocExists(tx: TransactionalSession?) = db.use(tx) {
        it.one(queryOf("SELECT count(*) FROM screen WHERE slide_set = 'adhoc'").map(asBoolean))
    }

    fun getAdHoc(): Either<AppError, Option<ScreenRow>> = db.use {
        it.option(queryOf("SELECT * FROM screen WHERE slide_set = ?", "adhoc").map(ScreenRow.fromRow))
    }

    fun getSlide(id: Int): Either<AppError, ScreenRow> = db.use {
        it.one(queryOf("SELECT * FROM screen WHERE id = ?", id).map(ScreenRow.fromRow))
    }

    fun getSlideSet(name: String): Either<AppError, List<ScreenRow>> = db.use {
        it.many(queryOf("SELECT * FROM screen WHERE slide_set = ? ORDER BY run_order, id", name).map(ScreenRow.fromRow))
    }

    inline fun <reified A : Slide<A>> setAdHoc(slide: A): Either<AppError, ScreenRow> = db.transaction { either {
        val (type, content) = getTypeAndJson(slide)
        val query = if (adHocExists(it).bind()) {
            "UPDATE screen SET type = ?, content = ?::jsonb WHERE slide_set = 'adhoc' RETURNING *"
        } else {
            "INSERT INTO screen(slide_set, type, content) VALUES('adhoc', ?, ?::jsonb) RETURNING *"
        }
        it.one(queryOf(query, type, content).map(ScreenRow.fromRow)).bind()
    } }

    inline fun <reified A : Slide<A>> add(slideSet: String, screen: A, tx: TransactionalSession? = null): Either<AppError, ScreenRow> = db.use(tx) {
        val (type, content) = getTypeAndJson(screen)
        it.one(queryOf(
            "INSERT INTO screen(slide_set, type, content, visible) VALUES(?, ?, ?::jsonb, false) RETURNING *",
            slideSet,
            type,
            content
        ).map(ScreenRow.fromRow))
    }

    inline fun <reified A : Slide<A>> update (id: Int, screen: A): Either<AppError, ScreenRow> = db.use {
        val (type, content) = getTypeAndJson(screen)
        it.one(queryOf("UPDATE screen SET type = ?, content = ?::jsonb WHERE id = ? RETURNING *", type, content, id).map(ScreenRow.fromRow))
    }

    inline fun <reified A : Slide<A>> replaceSlideSet(slideSet: String, slides: List<A>): Either<AppError, List<ScreenRow>> =
        db.transaction { either {
            val tx = it
            it.exec(queryOf("DELETE FROM screen WHERE slide_set = ?", slideSet)).bind()
            slides.map { slide -> add(slideSet, slide, tx) }.bindAll()
        } }

    fun getFirstSlide(slideSet: String): Either<AppError, ScreenRow> = db.use {
        it.one(queryOf("SELECT * FROM screen WHERE slide_set = ? ORDER BY run_order, id LIMIT 1", slideSet).map(ScreenRow.fromRow))
    }

    fun getNext(slideSet: String, currentId: Int): Either<AppError, ScreenRow> = either {
        val screens = getSlideSet(slideSet).bind()
        val index = positiveInt(screens.indexOfFirst { it.id == currentId })
            .toEither { DatabaseError("$currentId not in slide set '$slideSet'") }
            .bind()
        (screens.slice((index + 1)..<(screens.size)) + screens.slice(0..index))
            .filter { it.visible }
            .toNonEmptyListOrNone()
            .toEither { DatabaseError("No visible screens in slide set '$slideSet'") }
            .map { it.first() }
            .bind()
    }

    inline fun <reified A : Slide<A>> getTypeAndJson(screen: A) = Pair(
        screen.javaClass.name,
        Json.encodeToString(screen)
    )

    fun setVisible(id: Int, visible: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("UPDATE screen SET visible = ? WHERE id = ?", visible, id))
    }

    fun setRunOrder(id: Int, order: Int): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("UPDATE screen SET run_order = ? WHERE id = ?", order, id))
    }
}

data class ScreenRow(
    val id: Int,
    val slideSet: String,
    val type: String,
    val content: String,
    val visible: Boolean,
    val runOrder: Int,
) {
    fun getScreen(): Slide<*> =
        when(type) {
            TextSlide::class.qualifiedName -> Json.decodeFromString<TextSlide>(content)
            else -> TODO("JSON decoding not implemented for $type")
        }

   companion object {
       val fromRow: (Row) -> ScreenRow = { row ->
           ScreenRow(
               id = row.int("id"),
               slideSet = row.string("slide_set"),
               type = row.string("type"),
               content = row.string("content"),
               visible = row.boolean("visible"),
               runOrder = row.int("run_order"),
           )
       }
   }
}