package party.jml.partyboi.screen

import kotlinx.coroutines.flow.*
import kotlinx.html.FlowContent
import kotlinx.html.h1
import kotlinx.html.p
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field

class ScreenService(private val appServices: AppServices) {
    private val state = MutableStateFlow<Screen>(TextScreen("November Games", "Welcome, everyone!"))

    fun current(): Screen = state.value

    fun next(): Flow<Screen> {
        val current = state.value
        return state.filter { it != current }.take(1)
    }

    suspend fun show(screen: Screen) {
        state.emit(screen)
    }
}

interface Screen {
    fun render(ctx: FlowContent)
}

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
