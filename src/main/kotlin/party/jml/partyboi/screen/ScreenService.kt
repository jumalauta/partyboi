package party.jml.partyboi.screen

import arrow.atomic.update
import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.html.FlowContent
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule


class ScreenService(private val app: AppServices) {
    private val state = MutableStateFlow<Screen>(TextScreen.Empty)
    private val repository = ScreenRepository(app)
    private var scheduler: TimerTask? = null

    init {
        runBlocking {
            repository.getAdHoc().map { it.map { state.emit(it) } }
        }
    }

    fun current(): Screen = state.value

    fun next(): Flow<Screen> {
        val current = state.value
        return state.filter { it != current }.take(1)
    }

    suspend fun addAdHoc(screen: TextScreen): Either<AppError, Unit> = either {
        repository.addAdHoc(screen).bind()
        stopSlideShow()
        state.emit(screen)
    }

    fun stopSlideShow() {
        scheduler?.cancel()
        scheduler = null
    }

    fun startSlideShow(collection: String) {
        repository.getCollection(collection).map { screens ->
            val enabledScreens = screens.filter { it.enabled }.map { it.content }
            if (enabledScreens.isNotEmpty()) {
                stopSlideShow()
                val i = AtomicInteger(0)
                scheduler = Timer().schedule(0, 2000) {
                    val index = i.get()
                    runBlocking { state.emit(enabledScreens[index]) }
                    i.update { (it + 1) % enabledScreens.size }
                }
            }
        }
    }
}

@Serializable
sealed interface Screen {
    fun render(ctx: FlowContent)
}

@Serializable
data class TextScreen (
    @property:Field(order = 0, label = "Title")
    val title: String,
    @property:Field(order = 1, label = "Content", large = true)
    val content: String,
) : Screen, Validateable<TextScreen> {
    override fun render(ctx: FlowContent) {
        with(ctx) {
            h1 { +title }
            p { +content }
        }
    }

    companion object {
        val Empty = TextScreen("Hello, world!", "")
    }
}
