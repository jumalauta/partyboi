package party.jml.partyboi

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

class ScreenService(private val appServices: AppServices) {
    private val state = MutableStateFlow(Screen("November Games", "Welcome, everyone!"))

    fun current(): Screen = state.value

    fun next(): Flow<Screen> {
        val current = state.value
        return state.filter { it != current }.take(1)
    }

    suspend fun show(screen: Screen) {
        state.emit(screen)
    }
}

data class Screen(
    val title: String,
    val content: String,
)
