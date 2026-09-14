package party.jml

import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.delay
import party.jml.partyboi.AppServices
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.infoscreen.slides.TextSlide
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class InfoScreenSchedulerTest : PartyboiTester {

    // Regression: startAutoRunScheduler() overwrote the stored scheduler without cancelling the old
    // one, and only ever kept a TimerTask, so each (re)start leaked a non-daemon Timer thread that
    // kept advancing slides. Repeatedly starting and then stopping the scheduler must leave no extra
    // Timer threads behind.
    @Test
    fun testAutoRunSchedulerDoesNotLeakTimerThreads() = test {
        var appRef: AppServices? = null
        setupServices {
            appRef = this
            Unit.right()
        }

        it.login() // force application startup so appRef is populated
        val screen = appRef!!.screen

        // Start from a known state and let any prior scheduler thread exit.
        screen.stopSlideSet()
        delay(200)
        val before = timerThreadCount()

        repeat(5) { screen.startAutoRunScheduler() }
        screen.stopSlideSet()

        // Cancelled Timer threads exit asynchronously; wait until they have, or give up after a while.
        val deadline = before // capture for the message
        var after = timerThreadCount()
        repeat(40) {
            if (after <= before) return@repeat
            delay(50)
            after = timerThreadCount()
        }

        assertEquals(
            before,
            after,
            "repeatedly starting then stopping the auto-run scheduler leaked Timer threads ($deadline -> $after)",
        )
    }

    // Manually showing a slide while auto-run is on restarts the scheduler, so the
    // chosen slide gets a full slot instead of being replaced by an imminent tick.
    // The 10 s cadence itself is impractical to assert here; this covers the restart:
    // the chosen slide stays shown, auto-run stays on, and no timer threads leak.
    @Test
    fun testManualShowDuringAutoRunRestartsSchedulerWithoutLeaking() = test {
        var appRef: AppServices? = null
        var targetId: UUID? = null
        setupServices {
            appRef = this
            either {
                screen.addSlide(SlideSetRow.DEFAULT, TextSlide("One", "1"), makeVisible = true).bind()
                targetId = screen.addSlide(SlideSetRow.DEFAULT, TextSlide("Two", "2"), makeVisible = true).bind().id
            }
        }

        it.login() // force application startup so appRef is populated
        val screen = appRef!!.screen

        screen.stopSlideSet()
        delay(200)
        val before = timerThreadCount()

        screen.startSlideSet(SlideSetRow.DEFAULT).getOrNull()!!
        repeat(3) { screen.showStoredSlide(targetId!!).getOrNull()!! }

        val (state, autoRunning) = screen.currentState()
        assertEquals(targetId, state.id, "the manually chosen slide must stay shown")
        assertEquals(true, autoRunning, "auto-run must survive a manual show")

        screen.stopSlideSet()
        var after = timerThreadCount()
        repeat(40) {
            if (after <= before) return@repeat
            delay(50)
            after = timerThreadCount()
        }
        assertEquals(before, after, "manual show during auto-run leaked Timer threads ($before -> $after)")
    }

    // Count live java.util.Timer worker threads (the leaked ones are these, regardless of name).
    private fun timerThreadCount(): Int =
        Thread.getAllStackTraces().keys.count { it.isAlive && it.javaClass.name == "java.util.TimerThread" }
}
