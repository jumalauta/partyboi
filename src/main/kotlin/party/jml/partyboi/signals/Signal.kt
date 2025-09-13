package party.jml.partyboi.signals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.datetime.Clock
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.catchError
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.TimeService
import java.util.*

class SignalService(app: AppServices) : Service(app) {
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
        fun slideShown(slideId: UUID) = Signal(SignalType.slideShown, null, slideId.toString())
        fun eventStarted(eventId: UUID) = Signal(SignalType.event, null, eventId.toString())
        fun votingOpened(compoId: UUID) = Signal(SignalType.vote, "open", compoId.toString())
        fun votingClosed(compoId: UUID) = Signal(SignalType.vote, "close", compoId.toString())
        fun liveVotingOpened(compoId: UUID) = Signal(SignalType.liveVote, "open", compoId.toString())
        fun liveVotingClosed() = Signal(SignalType.liveVote, "close")
        fun liveVotingEntry(entryId: UUID) = Signal(SignalType.liveVote, "entry", entryId.toString())
        fun propertyUpdated(key: String) = Signal(SignalType.propertyUpdated, null, key)
        fun compoContentUpdated(compoId: UUID, timeService: TimeService) =
            Signal(SignalType.compoContentUpdated, timeService.isoLocalTime(), compoId.toString())

        fun fileUploaded(entryId: UUID, version: Int) =
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