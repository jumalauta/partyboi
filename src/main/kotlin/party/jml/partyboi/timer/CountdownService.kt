package party.jml.partyboi.timer

import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.Forbidden
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.infoscreen.slides.TimerSlide
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.triggers.Action
import party.jml.partyboi.triggers.PendingTriggerRow
import java.util.*
import kotlin.concurrent.schedule
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class TimerPhase { IDLE, RUNNING, PAUSED, FINISHED }

@Serializable
data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    @Serializable(with = UUIDSerializer::class)
    val timerId: UUID = UUIDv7.Empty,
    val endsAt: Instant? = null,
    val remainingMs: Long? = null,
    val message: String = "",
    val flashThresholdSecs: Int = 60,
    val resumeSlideSet: String? = null,
    val resumeSlideId: String? = null,
) {
    companion object {
        val Idle = TimerState()
    }
}

class CountdownService(app: AppServices) : Service(app) {
    private val state = property("state", TimerState.Idle).toState()
    private val mutex = Mutex()
    private var endTimer: Timer? = null

    init {
        // Recover a timer that was running when the process went down. The 5 s minimum delay
        // matters when the deadline already passed: the signal flow has replay = 0 and the
        // trigger collector is only launched in Application.module after service construction,
        // so firing immediately would drop the timerEnded signal and skip the end actions.
        val s = state.value
        if (s.phase == TimerPhase.RUNNING && s.endsAt != null) {
            scheduleEnd(s.endsAt, minDelayMs = 5000)
        }
    }

    fun currentState(): TimerState = state.value

    suspend fun start(minutes: Int, message: String, actions: List<Action>): AppResult<Unit> =
        start(minutes.minutes, message, actions)

    suspend fun start(duration: Duration, message: String, actions: List<Action>): AppResult<Unit> =
        mutex.withLock {
            either {
                val current = state.value
                if (current.phase == TimerPhase.RUNNING || current.phase == TimerPhase.PAUSED) {
                    raise(Forbidden())
                }
                val timerId = UUID.randomUUID()
                val (prevScreen, autoRunning) = app.screen.currentState()
                actions.forEach { app.triggers.add(Signal.timerEnded(timerId), it).bind() }
                val endsAt = Clock.System.now() + duration
                val next = TimerState(
                    phase = TimerPhase.RUNNING,
                    timerId = timerId,
                    endsAt = endsAt,
                    message = message,
                    resumeSlideSet = prevScreen.slideSet.takeIf { autoRunning },
                    resumeSlideId = prevScreen.id.toString(),
                )
                state.emit(next)
                scheduleEnd(endsAt)
                showSlide(next)
                app.signals.emit(Signal.timerUpdated())
            }
        }

    suspend fun updateMessage(message: String): AppResult<Unit> = mutex.withLock {
        either {
            val current = state.value
            if (current.phase == TimerPhase.IDLE) raise(Forbidden())
            val next = current.copy(message = message)
            state.emit(next)
            showSlide(next)
            app.signals.emit(Signal.timerUpdated())
        }
    }

    suspend fun pause(): AppResult<Unit> = mutex.withLock {
        either {
            val current = state.value
            if (current.phase != TimerPhase.RUNNING || current.endsAt == null) raise(Forbidden())
            cancelEndTimer()
            val remaining = (current.endsAt - Clock.System.now())
                .inWholeMilliseconds
                .coerceAtLeast(0)
            val next = current.copy(phase = TimerPhase.PAUSED, endsAt = null, remainingMs = remaining)
            state.emit(next)
            showSlide(next)
            app.signals.emit(Signal.timerUpdated())
        }
    }

    suspend fun resume(): AppResult<Unit> = mutex.withLock {
        either {
            val current = state.value
            if (current.phase != TimerPhase.PAUSED || current.remainingMs == null) raise(Forbidden())
            val endsAt = Clock.System.now() + current.remainingMs.milliseconds
            val next = current.copy(phase = TimerPhase.RUNNING, endsAt = endsAt, remainingMs = null)
            state.emit(next)
            scheduleEnd(endsAt)
            showSlide(next)
            app.signals.emit(Signal.timerUpdated())
        }
    }

    suspend fun addTime(minutes: Int = 1): AppResult<Unit> = mutex.withLock {
        either {
            val current = state.value
            val next = when (current.phase) {
                TimerPhase.RUNNING -> {
                    val endsAt = (current.endsAt ?: raise(Forbidden())) + minutes.minutes
                    scheduleEnd(endsAt)
                    current.copy(endsAt = endsAt)
                }

                TimerPhase.PAUSED ->
                    current.copy(remainingMs = (current.remainingMs ?: 0) + minutes.minutes.inWholeMilliseconds)

                else -> raise(Forbidden())
            }
            state.emit(next)
            showSlide(next)
            app.signals.emit(Signal.timerUpdated())
        }
    }

    // Abort without running the end actions: the timerEnded signal is never emitted, and the
    // pending triggers are disabled so the audit trail shows they never ran. (A fresh timerId
    // per run means they could not fire later anyway.)
    suspend fun stop(): AppResult<Unit> = mutex.withLock {
        either {
            cancelEndTimer()
            val current = state.value
            if (current.phase == TimerPhase.RUNNING || current.phase == TimerPhase.PAUSED) {
                app.triggers.getTriggersForSignal(Signal.timerEnded(current.timerId)).bind()
                    .filterIsInstance<PendingTriggerRow>()
                    .forEach { app.triggers.setEnabled(it.triggerId, false).bind() }
            }
            state.emit(TimerState.Idle)
            restoreScreen(current)
            app.signals.emit(Signal.timerUpdated())
        }
    }

    suspend fun dismiss(): AppResult<Unit> = mutex.withLock {
        either {
            val current = state.value
            if (current.phase != TimerPhase.FINISHED) raise(Forbidden())
            state.emit(TimerState.Idle)
            restoreScreen(current)
            app.signals.emit(Signal.timerUpdated())
        }
    }

    private suspend fun restoreScreen(s: TimerState) {
        // Best effort: the slide set restarts from its first slide, and a previously shown
        // in-memory or deleted slide cannot be brought back — the timer slide then stays up
        // until an admin shows something else.
        val restored = s.resumeSlideSet?.let { app.screen.startSlideSet(it).isRight() } ?: false
        if (!restored) {
            s.resumeSlideId?.let { app.screen.showStoredSlide(UUID.fromString(it)) }
        }
    }

    private fun scheduleEnd(endsAt: Instant, minDelayMs: Long = 0) {
        cancelEndTimer()
        val delay = maxOf(minDelayMs, (endsAt - Clock.System.now()).inWholeMilliseconds, 0)
        // Daemon thread, and always cancelled before being replaced — see the thread-leak note
        // in InfoScreenService.startAutoRunScheduler.
        endTimer = Timer("countdown-end", true).apply {
            schedule(delay) {
                runBlocking { onTimerEnd() }
            }
        }
    }

    private fun cancelEndTimer() {
        endTimer?.cancel()
        endTimer = null
    }

    private suspend fun onTimerEnd(): Unit = mutex.withLock {
        val current = state.value
        // A stale task that lost a race against pause/stop/addTime no-ops here.
        if (current.phase != TimerPhase.RUNNING || current.endsAt == null) return@withLock
        val remaining = (current.endsAt - Clock.System.now()).inWholeMilliseconds
        if (remaining > 0) {
            // Fired early (or the deadline moved): try again at the real deadline.
            scheduleEnd(current.endsAt)
            return@withLock
        }
        cancelEndTimer()
        val next = current.copy(phase = TimerPhase.FINISHED)
        state.emit(next)
        showSlide(next)
        app.signals.emit(Signal.timerEnded(current.timerId))
        app.signals.emit(Signal.timerUpdated())
    }

    private suspend fun showSlide(s: TimerState) = app.screen.showInMemorySlide(
        TimerSlide(
            message = s.message,
            endsAtEpochMs = s.endsAt?.toEpochMilliseconds(),
            remainingMs = s.remainingMs,
            flashThresholdSecs = s.flashThresholdSecs,
            finished = s.phase == TimerPhase.FINISHED,
        )
    )
}
