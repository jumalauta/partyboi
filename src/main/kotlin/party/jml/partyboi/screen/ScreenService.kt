package party.jml.partyboi.screen

import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.screen.slides.Slide
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.signals.SignalType
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.triggers.*
import party.jml.partyboi.voting.CompoResult
import java.util.*
import kotlin.concurrent.schedule
import kotlin.io.path.readText


class ScreenService(app: AppServices) : Service(app) {
    private val repository = ScreenRepository(app)
    private val state = property("state", ScreenState.Empty).toState()
    private var autoRunScheduler: TimerTask? = null
    private val autoRunOn = property("autoRunOn", false)

    init {
        runBlocking {
            repository.getAdHoc().map { it.map { state.emit(ScreenState.fromRow(it)) } }
            if (autoRunOn.getOrNull() == true) {
                startAutoRunScheduler()
            }
        }
    }

    suspend fun start() {
        app.signals.flow.collect {
            if (it.type == SignalType.compoContentUpdated && it.target != null) {
                val compoId = it.target.toInt()
                log.info("Update compo slides")
                generateSlidesForCompo(compoId)
            }
        }
    }

    suspend fun getSlideSets(): AppResult<List<SlideSetRow>> = repository.getSlideSets()
    suspend fun upsertSlideSet(id: String, name: String, icon: String): AppResult<Unit> =
        repository.upsertSlideSet(id, name, icon)

    fun currentState(): Pair<ScreenState, Boolean> = Pair(state.value, autoRunScheduler != null)
    fun currentSlide(): Slide<*> = state.value.slide

    fun waitForNext(): Flow<ScreenState> = state.waitForNext()

    suspend fun getAddHoc() = repository.getAdHoc()

    suspend fun addAdHoc(screen: TextSlide): AppResult<Unit> = either {
        val newState = ScreenState.fromRow(repository.setAdHoc(screen).bind())
        stopSlideSet()
        state.emit(newState)
    }

    suspend fun getSlide(slideId: Int): AppResult<ScreenRow> = repository.getSlide(slideId)
    suspend fun getAllSlides(): AppResult<List<ScreenRow>> = repository.getAllSlides()

    suspend fun getSlideSet(slideSet: String): AppResult<List<ScreenRow>> = repository.getSlideSetSlides(slideSet)

    suspend fun addSlide(slideSet: String, slide: Slide<*>) =
        repository.add(slideSet, slide, makeVisible = false, readOnly = false)

    suspend fun update(id: Int, slide: Slide<*>) = either {
        val updatedRow = repository.update(id, slide).bind()
        if (state.value.id == id) {
            show(updatedRow)
        }
        updatedRow
    }

    suspend fun delete(id: Int) = repository.delete(id)

    suspend fun deleteAll() = repository.deleteAll()

    suspend fun setVisible(id: Int, visible: Boolean) = repository.setVisible(id, visible)

    suspend fun showOnInfo(id: Int, visible: Boolean) = repository.showOnInfo(id, visible)

    suspend fun setRunOrder(id: Int, order: Int) = repository.setRunOrder(id, order)

    suspend fun stopSlideSet() {
        autoRunScheduler?.cancel()
        autoRunScheduler = null
        autoRunOn.set(false)
    }

    suspend fun startSlideSet(slideSetName: String): AppResult<Unit> =
        repository.getFirstSlide(slideSetName).map { firstScreen ->
            show(firstScreen)
            startAutoRunScheduler()
        }

    suspend fun startAutoRunScheduler() {
        autoRunScheduler = Timer().schedule(10000, 10000) {
            runBlocking {
                showNext()
            }
        }
        autoRunOn.set(true)
    }


    suspend fun show(slideId: Int) = either {
        show(repository.getSlide(slideId).bind())
    }

    suspend fun showNext() {
        repository.getNext(state.value.slideSet, state.value.id).fold(
            { stopSlideSet() },
            {
                stopSlideSet()
                show(it)
            }
        )
    }

    suspend fun showNextSlideFromSet(slideSetName: String): AppResult<Unit> =
        if (state.value.slideSet == slideSetName) {
            showNext().right()
        } else {
            repository.getFirstSlide(slideSetName).map { show(it) }
        }

    suspend fun generateSlidesForCompo(compoId: Int): AppResult<String> = either {
        val slideSet = "compo-${compoId}"
        val compo = app.compos.getById(compoId).bind()
        upsertSlideSet(slideSet, "Compo: ${compo.name}", "award")
        val entries = app.entries.getEntriesForCompo(compoId).bind().filter { it.qualified }

        val hypeSlides = listOf(TextSlide.compoStartsSoon(compo.name))
        val entrySlides = entries.mapIndexed { index, entry -> TextSlide.compoSlide(index, entry) }
        val endingSlides = listOf(TextSlide.compoHasEnded(compo.name))

        val allSlides = hypeSlides + entrySlides + endingSlides
        val dbRows = repository.replaceGeneratedSlideSet(slideSet, allSlides).bind()

        val firstShown = dbRows.first().whenShown()
        app.triggers.add(firstShown, OpenCloseSubmitting(compoId, false))
        app.triggers.add(firstShown, OpenLiveVoting(compoId))

        val entrySlideDbRows = dbRows.subList(hypeSlides.size, hypeSlides.size + entrySlides.size)
        entries.zip(entrySlideDbRows) { entry, slide ->
            app.triggers.add(slide.whenShown(), EnableLiveVotingForEntry(entry.id))
        }

        val lastShown = dbRows.last().whenShown()
        app.triggers.add(lastShown, CloseLiveVoting)
        app.triggers.add(lastShown, OpenCloseVoting(compoId, true))

        "/admin/screen/${slideSet}"
    }

    suspend fun generateResultSlidesForCompo(compoId: Int): AppResult<String> = either {
        val slideSet = "results-${compoId}"
        val compo = app.compos.getById(compoId).bind()
        upsertSlideSet(slideSet, "Results: ${compo.name}", "square-poll-horizontal")
        val results = app.votes.getResults().bind().filter { it.compoId == compoId }
        val resultsByPlace = CompoResult.groupResults(results).values.first()
        val resultsBySlide =
            resultsByPlace.fold(emptyList<List<CompoResult.Companion.GroupedCompoResult>>()) { acc, placeAndResult ->
                when (placeAndResult.place) {
                    in 1..4 -> acc + listOf(listOf(placeAndResult))
                    else -> {
                        val last = acc.last()
                        val entriesCollected = last.sumOf { it.results.size }
                        if (entriesCollected + placeAndResult.results.size > 5) {
                            acc + listOf(listOf(placeAndResult))
                        } else {
                            acc.take(acc.size - 1) + listOf(last + placeAndResult)
                        }
                    }
                }
            }.reversed()

        val hypeSlides = listOf(
            TextSlide("Next: ${compo.name} compo results", "", "compo-info")
        )
        val resultSlides = resultsBySlide.map { placeAndResults ->
            val places = placeAndResults.map { it.place }
            val minPlace = places.min()
            val maxPlace = places.max()
            val rows = placeAndResults.flatMap { pr ->
                pr.results.map { "* ${pr.place}. ${it.author} – ${it.title} (${it.points} pts.)" }
            }
            TextSlide(
                title = if (minPlace == 1) "Winner" else if (minPlace == maxPlace) "Place $minPlace" else "Places $minPlace-$maxPlace",
                rows.joinToString(separator = "\n"),
                "results"
            )
        }

        repository.replaceGeneratedSlideSet(slideSet, hypeSlides + resultSlides).bind()

        "/admin/screen/${slideSet}"
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

    fun import(tx: TransactionalSession, data: DataExport) = repository.import(tx, data)

    private suspend fun show(row: ScreenRow) {
        val slide = row.getSlide()
        if (slide is AutoRunHalting && slide.haltAutoRun()) {
            stopSlideSet()
        }
        state.emit(ScreenState.fromRow(row))
        return app.signals.emit(Signal.slideShown(row.id))
    }
}

@Serializable
data class ScreenState(
    val slideSet: String,
    val id: Int,
    val slide: Slide<*>,
) {
    companion object {
        val fromRow: (ScreenRow) -> ScreenState = { row ->
            ScreenState(
                slideSet = row.slideSet,
                id = row.id,
                slide = row.getSlide(),
            )
        }

        val Empty = ScreenState("adhoc", -1, TextSlide.Empty)
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