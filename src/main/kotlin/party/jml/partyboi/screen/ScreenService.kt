package party.jml.partyboi.screen

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

class ScreenService(private val app: AppServices) {
    private val state = MutableStateFlow<Screen>(TextScreen.Empty)
    private val repository = ScreenRepository(app)

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
        state.emit(screen)
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
