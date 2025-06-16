package party.jml.partyboi.signals

import arrow.core.Either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import party.jml.partyboi.Logging
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.catchError
import party.jml.partyboi.system.TimeService

class SignalService : Logging() {
    val flow = MutableStateFlow(Signal.initial())

    suspend fun emit(signal: Signal) {
        log.info("Emit signal: {}", signal)
        flow.emit(signal)
    }

    suspend fun waitFor(signalType: SignalType, onSignal: suspend (Signal) -> Unit) {
        flow
            .drop(1)
            .filter { it.type == signalType }
            .take(1)
            .collect {
                log.info("Collected: $it")
                onSignal(it)
            }
    }
}

data class Signal(
    val type: SignalType,
    val subType: String? = null,
    val target: String? = null,
) {
    override fun toString(): String = listOfNotNull(type, subType, target).joinToString(".")

    companion object {
        fun initial() = Signal(SignalType.initial)
        fun slideShown(slideId: Int) = Signal(SignalType.slideShown, null, slideId.toString())
        fun eventStarted(eventId: Int) = Signal(SignalType.event, null, eventId.toString())
        fun votingOpened(compoId: Int) = Signal(SignalType.vote, "open", compoId.toString())
        fun votingClosed(compoId: Int) = Signal(SignalType.vote, "close", compoId.toString())
        fun liveVotingOpened(compoId: Int) = Signal(SignalType.liveVote, "open", compoId.toString())
        fun liveVotingClosed() = Signal(SignalType.liveVote, "close")
        fun liveVotingEntry(entryId: Int) = Signal(SignalType.liveVote, "entry", entryId.toString())
        fun propertyUpdated(key: String) = Signal(SignalType.propertyUpdated, null, key)
        fun compoContentUpdated(compoId: Int, timeService: TimeService) =
            Signal(SignalType.compoContentUpdated, timeService.isoLocalTimeSync(), compoId.toString())
    }
}

enum class SignalType {
    initial,
    slideShown,
    event,
    vote,
    liveVote,
    propertyUpdated,
    compoContentUpdated;

    companion object {
        fun fromString(s: String): Either<AppError, SignalType> = catchError { SignalType.valueOf(s) }
    }
}