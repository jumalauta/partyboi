package party.jml.partyboi.infoscreen

import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.Forbidden
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.infoscreen.slides.ScheduleSlide
import party.jml.partyboi.infoscreen.slides.Slide
import party.jml.partyboi.infoscreen.slides.TextSlide
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.toDate
import java.util.*
import kotlin.concurrent.schedule
import kotlin.io.path.readText


class InfoScreenService(app: AppServices) : Service(app) {
    private val repository = InfoScreenRepository(app)
    private val state = property("state", InfoScreenState.Empty).toState()
    private var autoRunTimer: Timer? = null
    private val autoRunOn = property("autoRunOn", false)

    init {
        runBlocking {
            repository.getAdHoc().map { row -> row?.let { state.emit(InfoScreenState.fromRow(it)) } }
            if (autoRunOn.getOrNull() == true) {
                startAutoRunScheduler()
            }
        }
    }

    suspend fun getSlideSets(): AppResult<List<SlideSetRow>> = repository.getSlideSets()
    suspend fun upsertSlideSet(id: String, name: String, icon: String): AppResult<Unit> =
        repository.upsertSlideSet(id, name, icon)

    // Create a new slide set with a unique URL-friendly id derived from the name.
    // Returns the id of the created slide set so the caller can redirect to it.
    suspend fun createSlideSet(name: String): AppResult<String> = either {
        val taken = repository.getSlideSets().bind().map { it.id }.toSet() + reservedSlideSetIds
        val base = name.slugify().ifBlank { "slideset" }
        val id = generateSequence(0) { it + 1 }
            .map { if (it == 0) base else "$base-${it + 1}" }
            .first { it !in taken }
        repository.upsertSlideSet(id, name, "tv").bind()
        id
    }

    // Delete a slide set and all its slides (FK cascades). The built-in ad-hoc and
    // default sets are protected because other code paths assume they always exist.
    suspend fun deleteSlideSet(id: String): AppResult<Unit> =
        if (id == SlideSetRow.ADHOC || id == SlideSetRow.DEFAULT) {
            Forbidden().left()
        } else {
            repository.deleteSlideSet(id)
        }

    fun currentState(): Pair<InfoScreenState, Boolean> = Pair(state.value, autoRunTimer != null)
    fun currentSlide(): Slide<*> = state.value.slide

    fun waitForNext(): Flow<InfoScreenState> = state.waitForNext()

    suspend fun getAddHoc() = repository.getAdHoc()

    suspend fun addAdHoc(screen: TextSlide): AppResult<Unit> = either {
        val newState = InfoScreenState.fromRow(repository.setAdHoc(screen).bind())
        stopSlideSet()
        state.emit(newState)
    }

    suspend fun getSlide(slideId: UUID): AppResult<SlideRow> = repository.getSlide(slideId)
    suspend fun getAllSlides(): AppResult<List<SlideRow>> = repository.getAllSlides()

    suspend fun getSlideSet(slideSet: String): AppResult<List<SlideRow>> = repository.getSlideSetSlides(slideSet)

    suspend fun addSlide(slideSet: String, slide: Slide<*>, makeVisible: Boolean = false) =
        repository.add(slideSet, slide, makeVisible = makeVisible, readOnly = false)

    // Keep the slide set's schedule slides in sync with the dates that have a public
    // event: add a (visible) slide for any such date that lacks one, and remove slides
    // for dates that no longer have a public event. Idempotent, so it is safe to call
    // after any event change.
    suspend fun syncScheduleSlides(slideSet: String = SlideSetRow.DEFAULT): AppResult<Unit> = either {
        val publicDates = app.events.getPublic().bind()
            .map { it.startTime.toDate() }
            .toSet()
        val scheduleSlides = repository.getSlideSetSlides(slideSet).bind()
            .mapNotNull { row -> (row.getSlide() as? ScheduleSlide)?.let { it.date to row.id } }
        val existingDates = scheduleSlides.map { it.first }.toSet()

        scheduleSlides
            .filter { (date, _) -> date !in publicDates }
            .forEach { (_, id) -> repository.delete(id).bind() }

        publicDates.minus(existingDates).sorted().forEach { date ->
            repository.add(slideSet, ScheduleSlide(date), makeVisible = true, readOnly = false).bind()
        }
    }

    suspend fun update(id: UUID, slide: Slide<*>) = either {
        val updatedRow = repository.update(id, slide).bind()
        if (state.value.id == id) {
            showSlide(updatedRow)
        }
        updatedRow
    }

    suspend fun delete(id: UUID) = repository.delete(id)

    suspend fun deleteAll() = repository.deleteAll()

    suspend fun setVisible(id: UUID, visible: Boolean) = repository.setVisible(id, visible)

    suspend fun showOnInfo(id: UUID, visible: Boolean) = repository.showOnInfo(id, visible)

    suspend fun setRunOrder(id: UUID, order: Int) = repository.setRunOrder(id, order)

    suspend fun stopSlideSet() {
        autoRunTimer?.cancel()
        autoRunTimer = null
        autoRunOn.set(false)
    }

    suspend fun startSlideSet(slideSetName: String): AppResult<Unit> =
        repository.getFirstSlide(slideSetName).map { firstScreen ->
            showSlide(firstScreen)
            startAutoRunScheduler()
        }

    suspend fun startAutoRunScheduler() {
        // Cancel any running scheduler first, otherwise the old Timer's thread is leaked and both keep
        // advancing the slides. Store the Timer (not just the task) so it can be fully cancelled, and
        // run it on a daemon thread so a leftover scheduler can never keep the JVM alive.
        autoRunTimer?.cancel()
        autoRunTimer = Timer("infoscreen-autorun", true).apply {
            schedule(10000, 10000) {
                runBlocking {
                    showNext()
                }
            }
        }
        autoRunOn.set(true)
    }

    suspend fun showInMemorySlide(slide: Slide<*>) {
        if (slide is AutoRunHalting && slide.haltAutoRun()) {
            stopSlideSet()
        }
        val newState = InfoScreenState.fromSlide(slide)
        state.emit(newState)
        return app.signals.emit(Signal.slideShown(newState.id))
    }

    suspend fun showStoredSlide(slideId: UUID) = either {
        showSlide(repository.getSlide(slideId).bind())
    }

    suspend fun showNext() {
        state.value.slideSet?.let { slideSet ->
            repository.getNext(slideSet, state.value.id).fold(
                { stopSlideSet() },
                {
                    showSlide(it)
                }
            )
        }
    }

    suspend fun showNextSlideFromSet(slideSetName: String): AppResult<Unit> =
        if (state.value.slideSet == slideSetName) {
            showNext().right()
        } else {
            repository.getFirstSlide(slideSetName).map { showSlide(it) }
        }

    fun getThemeInfo(): ThemeInfo {
        return try {
            if (app.assets.exists(ThemeInfo.FILE_NAME)) {
                val content = app.assets.getFile(ThemeInfo.FILE_NAME).readText(Charsets.UTF_8)
                Json.decodeFromString<ThemeInfo>(content)
            } else {
                ThemeInfo.default
            }
        } catch (e: Exception) {
            ThemeInfo.default.copy(
                injectBody = "<!-- ERROR: $e -->",
            )
        }
    }

    private suspend fun showSlide(row: SlideRow) {
        val slide = row.getSlide()
        if (slide is AutoRunHalting && slide.haltAutoRun()) {
            stopSlideSet()
        }
        state.emit(InfoScreenState.fromRow(row))
        return app.signals.emit(Signal.slideShown(row.id))
    }
}

// Slide set ids that look like URLs handled by other routes under /admin/screen/.
// Generated slugs must avoid these to keep redirects working.
private val reservedSlideSetIds = setOf("new", "adhoc", "default", "slideset")

private fun String.slugify(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

@Serializable
data class InfoScreenState(
    val slideSet: String?,
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val slide: Slide<*>,
) {
    companion object {
        val fromRow: (SlideRow) -> InfoScreenState = { row ->
            InfoScreenState(
                slideSet = row.slideSet,
                id = row.id,
                slide = row.getSlide(),
            )
        }

        val fromSlide: (Slide<*>) -> InfoScreenState = { slide ->
            InfoScreenState(
                slideSet = null,
                id = UUID.randomUUID(),
                slide = slide,
            )
        }

        val Empty = InfoScreenState("adhoc", UUIDv7.Empty, TextSlide.Empty)
    }
}

interface AutoRunHalting {
    fun haltAutoRun(): Boolean
}

interface NonEditable

data class SlideType(
    val icon: String,
    val description: String,
)

@Serializable
data class ThemeInfo(
    val name: String,
    val width: Int,
    val height: Int,
    val injectBody: String? = null,
) {
    companion object {
        const val FILE_NAME = "screen/theme.json"

        val default = ThemeInfo(
            name = "Default theme",
            width = 1920,
            height = 1080,
            injectBody = "<!-- Default theme -->"
        )
    }
}