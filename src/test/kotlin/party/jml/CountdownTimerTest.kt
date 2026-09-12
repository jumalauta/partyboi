package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlinx.coroutines.delay
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.infoscreen.slides.TextSlide
import party.jml.partyboi.infoscreen.slides.TimerSlide
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.timer.TimerPhase
import party.jml.partyboi.triggers.OpenCloseSubmitting
import party.jml.partyboi.triggers.PendingTriggerRow
import party.jml.partyboi.triggers.SuccessfulTriggerRow
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CountdownTimerTest : PartyboiTester {
    @Test
    fun testTimerPageRequiresAdmin() = test {
        setupServices {
            either {
                countdown.stop().bind()
                addTestUser(this@setupServices).bind()
            }
        }
        it.login()
        it.get("/admin/screen/timer", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testStartTimerFromForm() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                countdown.stop().bind()
                addTestAdmin(this@setupServices).bind()
            }
        }
        it.login("admin")

        it.get("/admin/screen/timer", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Countdown timer") }
        }
        assertEquals(TimerPhase.IDLE, app!!.countdown.currentState().phase)

        it.post("/admin/screen/timer", formData {
            append("minutes", "15")
            append("message", "Fast graphics deadline")
            append("endAction", "NONE")
            append("compoId", UUIDv7.Empty.toString())
        }) { _ -> }

        val state = app!!.countdown.currentState()
        assertEquals(TimerPhase.RUNNING, state.phase)
        assertEquals("Fast graphics deadline", state.message)
        assertNotNull(state.endsAt)

        val slide = app!!.screen.currentSlide()
        assertIs<TimerSlide>(slide)
        assertEquals("Fast graphics deadline", slide.message)
        assertFalse(app!!.screen.currentState().second, "Slide rotation should halt while the timer runs")

        // The big screen renders the countdown with the data attributes screen.js binds to
        val body = it.client.get("/screen").bodyAsText()
        assertTrue("data-ends-at" in body, "Expected data-ends-at in screen HTML")
        assertTrue("Fast graphics deadline" in body, "Expected timer message in screen HTML")

        app!!.countdown.stop().getOrNull()
    }

    @Test
    fun testMessageIsEditableWhileRunning() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                countdown.stop().bind()
                addTestAdmin(this@setupServices).bind()
            }
        }
        it.login("admin")

        app!!.countdown.start(15, "Original", emptyList()).getOrNull()!!

        it.post("/admin/screen/timer/message", formData {
            append("message", "Five minutes left!")
        }) { _ -> }

        assertEquals("Five minutes left!", app!!.countdown.currentState().message)
        val slide = app!!.screen.currentSlide()
        assertIs<TimerSlide>(slide)
        assertEquals("Five minutes left!", slide.message)

        app!!.countdown.stop().getOrNull()
    }

    @Test
    fun testPauseResumeAndAddTime() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                countdown.stop().bind()
                addTestAdmin(this@setupServices).bind()
            }
        }
        startApplication()

        app!!.countdown.start(15, "", emptyList()).getOrNull()!!

        assertTrue(app!!.countdown.pause().isRight())
        val paused = app!!.countdown.currentState()
        assertEquals(TimerPhase.PAUSED, paused.phase)
        assertNull(paused.endsAt)
        val remaining = assertNotNull(paused.remainingMs)
        assertTrue(remaining in 1..15 * 60_000L, "Frozen remaining should be within the original duration")

        val pausedSlide = app!!.screen.currentSlide()
        assertIs<TimerSlide>(pausedSlide)
        assertNull(pausedSlide.endsAtEpochMs)
        assertEquals(remaining, pausedSlide.remainingMs)

        // Pausing twice is rejected
        assertTrue(app!!.countdown.pause().isLeft())

        assertTrue(app!!.countdown.resume().isRight())
        val resumed = app!!.countdown.currentState()
        assertEquals(TimerPhase.RUNNING, resumed.phase)
        assertNull(resumed.remainingMs)
        val endsAt = assertNotNull(resumed.endsAt)

        assertTrue(app!!.countdown.addTime(1).isRight())
        val extended = assertNotNull(app!!.countdown.currentState().endsAt)
        val added = (extended - endsAt).inWholeMilliseconds
        assertTrue(added in 59_000..61_000, "Add time should move the deadline by ~1 minute, moved $added ms")

        app!!.countdown.stop().getOrNull()
    }

    @Test
    fun testStopCancelsEndActions() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                countdown.stop().bind()
                addTestAdmin(this@setupServices).bind()
            }
        }

        startApplication()

        val compo = app!!.compos.add(NewCompo("Fast Graphics", "")).getOrNull()!!
        app!!.compos.allowSubmit(compo.id, true).getOrNull()!!

        app!!.countdown.start(15, "", listOf(OpenCloseSubmitting(compo.id, false))).getOrNull()!!
        val timerId = app!!.countdown.currentState().timerId

        val pending = app!!.triggers.getTriggersForSignal(Signal.timerEnded(timerId)).getOrNull()!!
        assertEquals(1, pending.filterIsInstance<PendingTriggerRow>().count { it.enabled })

        assertTrue(app!!.countdown.stop().isRight())
        assertEquals(TimerPhase.IDLE, app!!.countdown.currentState().phase)

        val afterStop = app!!.triggers.getTriggersForSignal(Signal.timerEnded(timerId)).getOrNull()!!
        assertTrue(
            afterStop.filterIsInstance<PendingTriggerRow>().all { !it.enabled },
            "Stopping must disable the pending end actions",
        )
        assertTrue(
            app!!.compos.getById(compo.id).getOrNull()!!.allowSubmit,
            "Compo must stay open for submissions after a cancelled timer",
        )
    }

    @Test
    fun testEndActionsRunWhenTimerExpires() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                countdown.stop().bind()
                addTestAdmin(this@setupServices).bind()
            }
        }
        // Hit an endpoint to make sure the application (and the trigger collector) is running
        it.login("admin")

        val compo = app!!.compos.add(NewCompo("Fast Graphics", "")).getOrNull()!!
        app!!.compos.allowSubmit(compo.id, true).getOrNull()!!

        app!!.countdown.start(500.milliseconds, "", listOf(OpenCloseSubmitting(compo.id, false))).getOrNull()!!
        val timerId = app!!.countdown.currentState().timerId

        val deadline = kotlin.time.Clock.System.now() + 15.seconds
        while (kotlin.time.Clock.System.now() < deadline) {
            val executed = app!!.triggers.getTriggersForSignal(Signal.timerEnded(timerId)).getOrNull()!!
                .any { it is SuccessfulTriggerRow }
            if (executed) break
            delay(200)
        }

        assertEquals(TimerPhase.FINISHED, app!!.countdown.currentState().phase)
        val slide = app!!.screen.currentSlide()
        assertIs<TimerSlide>(slide)
        assertTrue(slide.finished, "The slide should show the finished state")
        assertTrue(
            TimerSlide.TIMES_UP in it.client.get("/screen").bodyAsText(),
            "The screen should show the time's up text instead of 0:00",
        )

        val triggers = app!!.triggers.getTriggersForSignal(Signal.timerEnded(timerId)).getOrNull()!!
        assertTrue(triggers.any { it is SuccessfulTriggerRow }, "End action should have executed: $triggers")
        assertFalse(
            app!!.compos.getById(compo.id).getOrNull()!!.allowSubmit,
            "Compo should be closed for submissions after the timer ended",
        )

        // The finished view offers a shortcut to the closed compo's slide runner
        val finishedPage = it.client.get("/admin/screen/timer").bodyAsText()
        assertTrue(
            "/admin/compos/${compo.id}/run" in finishedPage,
            "Finished view should link to the compo slide runner",
        )

        assertTrue(app!!.countdown.dismiss().isRight())
        assertEquals(TimerPhase.IDLE, app!!.countdown.currentState().phase)
    }

    @Test
    fun testScreenIsRestoredAfterStop() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                countdown.stop().bind()
                addTestAdmin(this@setupServices).bind()
                screen.addSlide("default", TextSlide("Welcome", "to the party"), makeVisible = true).bind()
            }
        }
        startApplication()

        app!!.screen.startSlideSet("default").getOrNull()!!
        assertTrue(app!!.screen.currentState().second, "Autorun should be on before the timer")

        app!!.countdown.start(15, "", emptyList()).getOrNull()!!
        assertFalse(app!!.screen.currentState().second, "Autorun should halt while the timer runs")

        assertTrue(app!!.countdown.stop().isRight())
        val (screenState, isRunning) = app!!.screen.currentState()
        assertEquals("default", screenState.slideSet)
        assertTrue(isRunning, "Autorun should be restored after stopping the timer")
    }
}
