package party.jml.partyboi.signals

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlin.time.Clock
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.catchError
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.TimeService
import java.util.*

class SignalService(app: AppServices) : Service(app) {
    // The event bus used to be a conflating MutableStateFlow: while a collector (e.g. the trigger
    // runner) was busy handling one signal, any further distinct signals emitted in the same tick
    // were dropped, so the collector only ever saw the latest. A buffered SharedFlow keeps each
    // signal; a slow collector is backpressured (emit suspends once 64 are pending) rather than
    // having intermediate signals silently discarded. replay = 0 because nobody should receive
    // signals emitted before they started listening (which is also why getNext no longer drops one).
    val flow = MutableSharedFlow<Signal>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    suspend fun emit(signal: Signal) {
        log.trace("Emit signal: {}", signal)
        flow.emit(signal)
    }

    suspend fun getNext(signalType: SignalType, onSignal: suspend (Signal) -> Unit) {
        flow
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
        suspend fun compoContentUpdated(compoId: UUID, timeService: TimeService) =
            Signal(SignalType.compoContentUpdated, timeService.isoLocalTime(), compoId.toString())
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