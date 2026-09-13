package party.jml.partyboi.infoscreen

import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.data.throwOnError
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.form.Label
import party.jml.partyboi.infoscreen.slides.*
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.NavItem
import party.jml.partyboi.validation.MaxLength
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import java.util.*

class InfoScreenRepository(app: AppServices) : Service(app) {
    val db = app.db
    val assets = app.assets

    init {
        runBlocking {
            upsertSlideSet(SlideSetRow.ADHOC, "Ad hoc", "bolt").throwOnError()
            upsertSlideSet(SlideSetRow.DEFAULT, "Default", "circle-info").throwOnError()
        }
    }

    suspend fun upsertSlideSet(id: String, name: String, icon: String, tx: TransactionalSession? = null) = db.use(tx) {
        exec(
            queryOf(
                """
                INSERT INTO slideset (id, name, icon)
                    VALUES (?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name,
                        icon = EXCLUDED.icon
                """,
                id,
                name,
                icon,
            )
        )
    }

    suspend fun getSlideSets(): AppResult<List<SlideSetRow>> = db.use {
        many(queryOf("SELECT * FROM slideset ORDER BY name").map(SlideSetRow.fromRow))
    }

    suspend fun getSlideSet(id: String): AppResult<SlideSetRow> = db.use {
        one(queryOf("SELECT * FROM slideset WHERE id = ?", id).map(SlideSetRow.fromRow))
    }

    suspend fun setMaxImageSlides(id: String, value: Int?, tx: TransactionalSession? = null): AppResult<Unit> =
        db.use(tx) {
            updateOne(queryOf("UPDATE slideset SET max_image_slides = ? WHERE id = ?", value, id))
        }

    suspend fun adHocExists(tx: TransactionalSession?) = db.use(tx) {
        one(queryOf("SELECT count(*) FROM slide WHERE slideset_id = ?", SlideSetRow.ADHOC).map(asBoolean))
    }

    suspend fun getAdHoc(): AppResult<SlideRow?> = db.use {
        option(queryOf("SELECT * FROM slide WHERE slideset_id = ?", SlideSetRow.ADHOC).map(SlideRow.fromRow))
    }

    suspend fun getSlide(id: UUID): AppResult<SlideRow> = db.use {
        one(queryOf("SELECT * FROM slide WHERE id = ?", id).map(SlideRow.fromRow))
    }

    suspend fun getAllSlides(): AppResult<List<SlideRow>> = db.use {
        many(queryOf("SELECT * FROM slide").map(SlideRow.fromRow))
    }

    suspend fun getSlideSetSlides(name: String): AppResult<List<SlideRow>> = db.use {
        many(
            queryOf(
                "SELECT * FROM slide WHERE slideset_id = ? ORDER BY run_order, id",
                name
            ).map(SlideRow.fromRow)
        )
    }

    suspend fun setAdHoc(slide: Slide<*>): AppResult<SlideRow> = db.transaction {
        either {
            val (type, content) = getTypeAndJson(slide)
            val query = if (adHocExists(this@transaction).bind()) {
                "UPDATE slide SET type = ?, content = ?::jsonb WHERE slideset_id = '${SlideSetRow.ADHOC}' RETURNING *"
            } else {
                "INSERT INTO slide(slideset_id, type, content) VALUES('adhoc', ?, ?::jsonb) RETURNING *"
            }
            one(queryOf(query, type, content).map(SlideRow.fromRow)).bind()
        }
    }

    suspend fun add(
        slideSet: String,
        slide: Slide<*>,
        makeVisible: Boolean,
        readOnly: Boolean,
        tx: TransactionalSession? = null
    ): AppResult<SlideRow> = db.use(tx) {
        val (type, content) = getTypeAndJson(slide)
        one(
            queryOf(
                "INSERT INTO slide(slideset_id, type, content, visible, readonly) VALUES(?, ?, ?::jsonb, ?, ?) RETURNING *",
                slideSet,
                type,
                content,
                makeVisible,
                readOnly,
            ).map(SlideRow.fromRow)
        )
    }

    suspend fun update(id: UUID, slide: Slide<*>): AppResult<SlideRow> = db.use {
        val (type, content) = getTypeAndJson(slide)
        one(
            queryOf(
                "UPDATE slide SET type = ?, content = ?::jsonb WHERE id = ? AND NOT readonly RETURNING *",
                type,
                content,
                id
            ).map(SlideRow.fromRow)
        )
    }

    suspend fun delete(id: UUID): AppResult<Unit> = db.use {
        updateOne(queryOf("DELETE FROM slide WHERE id = ?", id))
    }

    // Removes the slide set row. The slide.slideset_id FK is ON DELETE CASCADE,
    // so any slides in the set are removed too.
    suspend fun deleteSlideSet(id: String): AppResult<Unit> = db.use {
        updateOne(queryOf("DELETE FROM slideset WHERE id = ?", id))
    }

    // Full reset for tests: also removes non-builtin slide sets and clears the builtin
    // sets' per-set settings, so state cannot leak from one test to the next.
    suspend fun deleteAll(): AppResult<Unit> = db.transaction {
        either {
            exec(queryOf("DELETE FROM slide")).bind()
            exec(
                queryOf(
                    "DELETE FROM slideset WHERE id NOT IN (?, ?)",
                    SlideSetRow.ADHOC,
                    SlideSetRow.DEFAULT
                )
            ).bind()
            exec(queryOf("UPDATE slideset SET max_image_slides = NULL")).bind()
        }
    }

    suspend fun replaceGeneratedSlideSet(slideSet: String, slides: List<Slide<*>>): AppResult<List<SlideRow>> =
        db.transaction {
            either {
                exec(queryOf("DELETE FROM slide WHERE slideset_id = ? AND readonly", slideSet)).bind()
                slides.map { slide -> add(slideSet, slide, makeVisible = true, readOnly = true, this@transaction) }
                    .bindAll()
            }
        }

    suspend fun setVisible(id: UUID, visible: Boolean): AppResult<Unit> = db.use {
        updateOne(queryOf("UPDATE slide SET visible = ? WHERE id = ?", visible, id))
    }

    suspend fun showOnInfo(id: UUID, visible: Boolean): AppResult<Unit> = db.use {
        updateOne(queryOf("UPDATE slide SET show_on_info = ? WHERE id = ?", visible, id))
    }

    suspend fun setRunOrder(id: UUID, order: Int): AppResult<Unit> = db.use {
        updateOne(queryOf("UPDATE slide SET run_order = ? WHERE id = ?", order, id))
    }

    private fun getTypeAndJson(slide: Slide<*>) = Pair(slide.javaClass.name, slide.toJson())
}

@Serializable
data class SlideSetRow(
    val id: String,
    val name: String,
    val icon: String,
    // Max number of image slides shown per rotation pass; null = no limit.
    val maxImageSlides: Int? = null,
) {
    fun toNavItem() = NavItem("/admin/screen/$id", name)

    companion object {
        const val ADHOC = "adhoc"
        const val DEFAULT = "default"

        val fromRow: (Row) -> SlideSetRow = { row ->
            SlideSetRow(
                id = row.string("id"),
                name = row.string("name"),
                icon = row.string("icon"),
                maxImageSlides = row.intOrNull("max_image_slides"),
            )
        }
    }
}

data class NewSlideSet(
    @Label("Name")
    @NotEmpty
    @MaxLength(64)
    val name: String,
) : Validateable<NewSlideSet> {
    companion object {
        val Empty = NewSlideSet("")
    }
}

@Serializable
data class SlideRow(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val slideSet: String,
    val type: String,
    val content: String,
    val visible: Boolean,
    val runOrder: Int,
    val showOnInfoPage: Boolean,
    val readOnly: Boolean,
) {
    fun getSlide(): Slide<*> =
        when (type) {
            TextSlide::class.qualifiedName -> Json.decodeFromString<TextSlide>(content)
            QrCodeSlide::class.qualifiedName -> Json.decodeFromString<QrCodeSlide>(content)
            ImageSlide::class.qualifiedName -> Json.decodeFromString<ImageSlide>(content)
            ScheduleSlide::class.qualifiedName -> Json.decodeFromString<ScheduleSlide>(content)
            TimerSlide::class.qualifiedName -> Json.decodeFromString<TimerSlide>(content)
            else -> TODO("JSON decoding not implemented for $type")
        }

    fun whenShown(): Signal = Signal.slideShown(id)

    companion object {
        val fromRow: (Row) -> SlideRow = { row ->
            SlideRow(
                id = row.uuid("id"),
                slideSet = row.string("slideset_id"),
                type = row.string("type"),
                content = row.string("content"),
                visible = row.boolean("visible"),
                runOrder = row.int("run_order"),
                showOnInfoPage = row.boolean("show_on_info"),
                readOnly = row.boolean("readonly"),
            )
        }
    }
}
