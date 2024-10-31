package party.jml.partyboi.signals

import kotlinx.coroutines.flow.MutableSharedFlow

class SignalService {
    val flow = MutableSharedFlow<Signal>()

    suspend fun emit(type: SignalType, target: String? = null) {
        flow.emit(Signal(type, target))
    }
}

data class Signal(
    val type: SignalType,
    val target: String?
) {
    override fun toString(): String = if (target != null) "${type.name}.$target" else type.name
}

enum class SignalType {
    slideShown
}