package party.jml.partyboi.signals

import kotlinx.coroutines.flow.MutableSharedFlow
import party.jml.partyboi.Logging

class SignalService : Logging() {
    val flow = MutableSharedFlow<Signal>()

    suspend fun emit(type: SignalType, target: String? = null) {
        emit(Signal(type, target))
    }

    suspend fun emit(signal: Signal) {
        log.info("Emit signal: {}", signal)
        flow.emit(signal)
    }
}

data class Signal(
    val type: SignalType,
    val target: String?
) {
    override fun toString(): String = if (target != null) "${type.name}.$target" else type.name
}

enum class SignalType {
    slideShown,
    event,
}