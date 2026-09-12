package party.jml.partyboi.timer

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.infoscreen.admin.AdminScreenPage
import party.jml.partyboi.infoscreen.admin.postButton
import party.jml.partyboi.infoscreen.slides.TimerSlide
import party.jml.partyboi.signals.SignalType
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonGroup
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.refreshOnSignal
import party.jml.partyboi.triggers.FailedTriggerRow
import party.jml.partyboi.triggers.OpenCloseSubmitting
import party.jml.partyboi.triggers.PendingTriggerRow
import party.jml.partyboi.triggers.SuccessfulTriggerRow
import party.jml.partyboi.triggers.TriggerRow
import kotlin.time.Clock

object AdminTimerPage {
    fun render(
        state: TimerState,
        triggers: List<TriggerRow>,
        compos: List<Compo>,
        slideSets: List<SlideSetRow>,
        startForm: Form<StartTimer>? = null,
        messageForm: Form<TimerMessage>? = null,
    ) = Page(
        title = "Timer",
        subLinks = AdminScreenPage.generateSubLinks(slideSets),
    ) {
        h1 { +"Countdown timer" }
        with(AdminScreenPage) {
            renderWithScreenMonitoring(false) {
                when (state.phase) {
                    TimerPhase.IDLE -> renderStartForm(startForm, compos)
                    TimerPhase.RUNNING, TimerPhase.PAUSED -> renderRunning(state, triggers, messageForm)
                    TimerPhase.FINISHED -> renderFinished(state, triggers, compos)
                }
            }
        }
        refreshOnSignal(SignalType.timer)
    }

    private fun FlowContent.renderStartForm(startForm: Form<StartTimer>?, compos: List<Compo>) {
        renderForm(
            url = "/admin/screen/timer",
            form = startForm ?: Form(StartTimer::class, StartTimer.Empty, initial = true),
            title = "Start a timer",
            submitButtonLabel = "Start timer",
            options = mapOf(
                "endAction" to StartTimer.ActionOptions,
                "compoId" to compos,
            ),
        )
    }

    private fun FlowContent.renderRunning(
        state: TimerState,
        triggers: List<TriggerRow>,
        messageForm: Form<TimerMessage>?,
    ) {
        val paused = state.phase == TimerPhase.PAUSED
        article {
            cardHeader(if (paused) "Timer paused" else "Timer running")
            p(classes = "admin-countdown") {
                id = "admin-countdown"
                state.endsAt?.let { attributes["data-ends-at"] = it.toEpochMilliseconds().toString() }
                attributes["data-server-now"] = Clock.System.now().toEpochMilliseconds().toString()
                +TimerSlide.formatRemaining(
                    state.remainingMs
                        ?: state.endsAt?.let { (it - Clock.System.now()).inWholeMilliseconds }
                        ?: 0
                )
            }
            buttonGroup {
                if (paused) {
                    postButton("/admin/screen/timer/resume") { +"Resume" }
                } else {
                    postButton("/admin/screen/timer/pause") { +"Pause" }
                }
                postButton("/admin/screen/timer/add-time") { +"+1 min" }
                button {
                    onClick = Javascript.build {
                        confirm("Stop the timer? The end actions will not be run.") {
                            httpPost("/admin/screen/timer/stop")
                            refresh()
                        }
                    }
                    +"Stop timer"
                }
            }
        }

        renderForm(
            url = "/admin/screen/timer/message",
            form = messageForm ?: Form(TimerMessage::class, TimerMessage(state.message), initial = true),
            title = "Message",
            submitButtonLabel = "Update message",
        )

        val pending = triggers.filterIsInstance<PendingTriggerRow>().filter { it.enabled }
        if (pending.isNotEmpty()) {
            article {
                cardHeader("When the timer ends")
                ul { pending.forEach { li { +it.description } } }
            }
        }
        // The remaining-time ticker is bound by initAdminCountdown() in partyboi.js:
        // an inline script here would die on the first smooth reload (innerHTML swap).
    }

    private fun FlowContent.renderFinished(state: TimerState, triggers: List<TriggerRow>, compos: List<Compo>) {
        article {
            cardHeader("Timer finished")
            if (state.message.isNotBlank()) {
                p { +state.message }
            }
            if (triggers.isNotEmpty()) {
                ul {
                    triggers.forEach { trigger ->
                        li {
                            when (trigger) {
                                is SuccessfulTriggerRow -> +"${trigger.description} — done"
                                is FailedTriggerRow -> +"${trigger.description} — FAILED: ${trigger.error}"
                                is PendingTriggerRow ->
                                    +"${trigger.description} — ${if (trigger.enabled) "pending" else "cancelled"}"
                            }
                        }
                    }
                }
            }
            buttonGroup {
                postButton("/admin/screen/timer/dismiss") { +"Back to slides" }
                // A close-submitting timer usually means the compo runs next: offer a
                // shortcut to its slide runner.
                closedCompos(triggers, compos).forEach { compo ->
                    a(href = "/admin/compos/${compo.id}/run") {
                        role = "button"
                        +"Run ${compo.displayName} slides"
                    }
                }
            }
        }
    }

    private fun closedCompos(triggers: List<TriggerRow>, compos: List<Compo>): List<Compo> =
        triggers
            .mapNotNull { it.getAction().getOrNull() as? OpenCloseSubmitting }
            .filter { !it.open }
            .mapNotNull { action -> compos.find { it.id == action.compoId } }
}
