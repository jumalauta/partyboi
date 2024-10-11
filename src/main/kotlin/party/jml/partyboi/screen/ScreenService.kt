package party.jml.partyboi.screen

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.raise.either
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.FlowContent
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.Form
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

    fun current(): Screen<*> = state.value.screen

    fun waitForNext(): Flow<Screen<*>> {
        val current = state.value
        return state.filter { it != current }.take(1).map { it.screen }
    }

    fun currentlyRunningCollection(): Option<String> = if (scheduler == null) None else Some(state.value.collection)

    suspend fun addAdHoc(screen: TextScreen): Either<AppError, Unit> = either {
        val newState = ScreenState.fromRow(repository.setAdHoc(screen).bind())
        stopSlideShow()
        state.emit(newState)
    }

    fun getCollection(collection: String): Either<AppError, List<ScreenRow>> = repository.getCollection(collection)

    inline fun <reified A : Screen<A>> addEmptyToCollection(collection: String, screen: A) = repository.add(collection, screen)

    inline fun <reified A : Screen<A>> update(id: Int, screen: A) = repository.update(id, screen)

    fun setVisible(id: Int, visible: Boolean) = repository.setVisible(id, visible)

    fun stopSlideShow() {
        scheduler?.cancel()
        scheduler = null
    }

    fun startSlideShow(collection: String): Either<AppError, Unit> =
        repository.getFirst(collection).map { firstScreen ->
            show(firstScreen)
            scheduler = Timer().schedule(0, 10000) {
                repository.getNext(state.value.collection, state.value.id).fold(
                    { stopSlideShow() },
                    { show(it) }
                )
            }
        }

    private fun show(row: ScreenRow): Unit =
        runBlocking {
            state.emit(ScreenState.fromRow(row))
        }
}

data class ScreenState(
    val collection: String,
    val id: Int,
    val screen: Screen<*>,
) {
    companion object {
        val fromRow: (ScreenRow) -> ScreenState = { row ->
            ScreenState(
                collection = row.collection,
                id = row.id,
                screen = row.getScreen(),
            )
        }

        val Empty = ScreenState("adhoc", -1, TextScreen.Empty)
    }
}

@Serializable
sealed interface Screen<A>
where A: Screen<A>,
      A: Validateable<A> {
    fun render(ctx: FlowContent)
    fun getForm(): Form<A>
    fun getName(): String
}

@Serializable
data class TextScreen (
    @property:Field(order = 0, label = "Title")
    val title: String,
    @property:Field(order = 1, label = "Content", large = true)
    val content: String,
) : Screen<TextScreen>, Validateable<TextScreen> {
    override fun render(ctx: FlowContent) {
        with(ctx) {
            h1 { +title }
            p { +content }
        }
    }

    override fun getForm(): Form<TextScreen> = Form(TextScreen::class, this, true)
    override fun getName(): String = title

    companion object {
        val Empty = TextScreen("Hello, world!", "")
    }
}
