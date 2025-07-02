package party.jml.partyboi.signals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.datetime.Clock
import party.jml.partyboi.Logging
import party.jml.partyboi.data.catchError
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.TimeService

class SignalService : Logging() {
    val flow = MutableStateFlow(Signal.initial())

    suspend fun emit(signal: Signal) {
        log.trace("Emit signal: {}", signal)
        flow.emit(signal)
    }

    suspend fun getNext(signalType: SignalType, onSignal: suspend (Signal) -> Unit) {
        flow
            .drop(1)
            .filter { it.type == signalType }
            .take(1)
            .collect { onSignal(it) }
    }
}

data class Signal(
    val type: SignalType,
    val subType: String? = null,
    val target: String? = null,
    val time: Long = Clock.System.now().toEpochMilliseconds(),
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
            Signal(SignalType.compoContentUpdated, timeService.isoLocalTime(), compoId.toString())

        fun fileUploaded(entryId: Int, version: Int) =
            Signal(SignalType.fileUploaded, entryId.toString(), version.toString())
    }
}

enum class SignalType {
    initial,
    slideShown,
    event,
    vote,
    liveVote,
    propertyUpdated,
    compoContentUpdated,
    fileUploaded;

    companion object {
        fun fromString(s: String): AppResult<SignalType> = catchError { SignalType.valueOf(s) }
    }
}