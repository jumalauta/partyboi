package party.jml.partyboi.screen

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.some
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.html.FlowContent
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.slides.TextSlide
import java.util.*
import kotlin.concurrent.schedule


class ScreenService(private val app: AppServices) {
    private val state = MutableStateFlow(ScreenState.Empty)
    val repository = ScreenRepository(app)
    private var scheduler: TimerTask? = null

    init {
        runBlocking {
            repository.getAdHoc().map { it.map { state.emit(ScreenState.fromRow(it)) } }
        }
    }

    fun currentState(): Pair<ScreenState, Boolean> = Pair(state.value, scheduler != null)
    fun currentSlide(): Slide<*> = state.value.slide

    fun waitForNext(): Flow<ScreenState> {
        val current = state.value
        return state.filter { it != current }.take(1)
    }

    fun waitForNext(currentId: Int?): Flow<ScreenState> =
        if (currentId == null) {
            waitForNext()
        } else {
            state.filter { it.id != currentId }.take(1)
        }

    suspend fun addAdHoc(screen: TextSlide): Either<AppError, Unit> = either {
        val newState = ScreenState.fromRow(repository.setAdHoc(screen).bind())
        stopSlideSet()
        state.emit(newState)
    }

    fun getSlide(slideId: Int): Either<AppError, ScreenRow> = repository.getSlide(slideId)

    fun getSlideSet(slideSet: String): Either<AppError, List<ScreenRow>> = repository.getSlideSet(slideSet)

    fun addEmptySlideToSlideSet(slideSet: String, slide: Slide<*>) = repository.add(slideSet, slide, makeVisible = false)

    fun update(id: Int, slide: Slide<*>) = repository.update(id, slide)

    fun setVisible(id: Int, visible: Boolean) = repository.setVisible(id, visible)

    fun setRunOrder(id: Int, order: Int) = repository.setRunOrder(id, order)

    fun stopSlideSet() {
        scheduler?.cancel()
        scheduler = null
    }

    fun startSlideSet(slideSetName: String): Either<AppError, Unit> =
        repository.getFirstSlide(slideSetName).map { firstScreen ->
            show(firstScreen)
            scheduler = Timer().schedule(10000, 10000) {
                showNext()
            }
        }

    fun show(slideId: Int) = either {
        show(repository.getSlide(slideId).bind())
    }

    fun showNext() {
        repository.getNext(state.value.slideSet, state.value.id).fold(
            { stopSlideSet() },
            { show(it) }
        )
    }

    fun generateSlidesForCompo(compoId: Int): Either<AppError, String> = either {
        val slideSet = "compo-${compoId}"
        val compo = app.compos.getById(compoId).bind()
        val entries = app.entries.getEntriesForCompo(compoId).bind().filter { it.qualified }

        val slides = listOf(
            TextSlide("${compo.name} compo starts soon", "")
        ) + entries.mapIndexed { index, entry ->
            TextSlide(
                "#${index + 1} ${entry.title}",
                listOf(entry.author.some(), entry.screenComment)
                    .flatMap { it.toList() }
                    .joinToString(separator = "\n\n") { it }
            )
        } + listOf(
            TextSlide("${compo.name} compo has ended", "")
        )

        repository.replaceSlideSet(slideSet, slides).bind()

        "/admin/screen/${slideSet}"
    }

    private fun show(row: ScreenRow): Unit =
        runBlocking {
            state.emit(ScreenState.fromRow(row))
        }
}

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

interface Slide<A: Validateable<A>> {
    fun render(ctx: FlowContent)
    fun getForm(): Form<A>
    fun toJson(): String
    fun getName(): String
}

