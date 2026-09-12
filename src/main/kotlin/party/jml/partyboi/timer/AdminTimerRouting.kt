package party.jml.partyboi.timer

import arrow.core.Option
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Large
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.triggers.Action
import party.jml.partyboi.triggers.CloseVotingForAllCompos
import party.jml.partyboi.triggers.OpenCloseSubmitting
import party.jml.partyboi.validation.Min
import party.jml.partyboi.validation.Validateable
import java.util.*

fun Application.configureAdminTimerRouting(app: AppServices) {
    suspend fun renderTimerPage(
        startForm: Form<StartTimer>? = null,
        messageForm: Form<TimerMessage>? = null,
    ): AppResult<Page> = either {
        val state = app.countdown.currentState()
        val triggers =
            if (state.phase == TimerPhase.IDLE) emptyList()
            else app.triggers.getTriggersForSignal(Signal.timerEnded(state.timerId)).bind()
        AdminTimerPage.render(
            state = state,
            triggers = triggers,
            compos = app.compos.getAllCompos().bind(),
            slideSets = app.screen.getSlideSets().bind(),
            startForm = startForm,
            messageForm = messageForm,
        )
    }

    adminRouting {
        get("/admin/screen/timer") {
            call.respondEither { renderTimerPage().bind() }
        }

        post("/admin/screen/timer") {
            call.processForm<StartTimer>(
                {
                    app.countdown.start(it.minutes, it.message, it.toActions())
                        .map { Redirection("/admin/screen/timer") }
                        .bind()
                },
                { form -> renderTimerPage(startForm = form).bind() },
            )
        }

        post("/admin/screen/timer/message") {
            call.processForm<TimerMessage>(
                {
                    app.countdown.updateMessage(it.message)
                        .map { Redirection("/admin/screen/timer") }
                        .bind()
                },
                { form -> renderTimerPage(messageForm = form).bind() },
            )
        }
    }

    adminApiRouting {
        post("/admin/screen/timer/pause") {
            call.apiRespond {
                call.userSession(app).bind()
                app.countdown.pause().bind()
            }
        }
        post("/admin/screen/timer/resume") {
            call.apiRespond {
                call.userSession(app).bind()
                app.countdown.resume().bind()
            }
        }
        post("/admin/screen/timer/add-time") {
            call.apiRespond {
                call.userSession(app).bind()
                app.countdown.addTime(1).bind()
            }
        }
        post("/admin/screen/timer/stop") {
            call.apiRespond {
                call.userSession(app).bind()
                app.countdown.stop().bind()
            }
        }
        post("/admin/screen/timer/dismiss") {
            call.apiRespond {
                call.userSession(app).bind()
                app.countdown.dismiss().bind()
            }
        }
    }
}

data class StartTimer(
    @Field("Duration (minutes)")
    @Min(1)
    val minutes: Int,
    @Field("Message shown with the timer")
    @Large
    val message: String,
    @Field("When the timer ends")
    val endAction: String,
    @Field("Compo")
    val compoId: UUID,
) : Validateable<StartTimer> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        cond(
            "endAction",
            endAction,
            EndAction.entries.none { it.name == endAction },
            "Unknown action",
        ),
        cond(
            "compoId",
            compoId.toString(),
            endAction == EndAction.CLOSE_SUBMITTING.name && compoId == UUIDv7.Empty,
            "Choose the compo to close",
        ),
    )

    fun toActions(): List<Action> = when (EndAction.valueOf(endAction)) {
        EndAction.NONE -> emptyList()
        EndAction.CLOSE_SUBMITTING -> listOf(OpenCloseSubmitting(compoId, false))
        EndAction.CLOSE_VOTING_ALL -> listOf(CloseVotingForAllCompos)
    }

    enum class EndAction { NONE, CLOSE_SUBMITTING, CLOSE_VOTING_ALL }

    companion object {
        val Empty = StartTimer(15, "", EndAction.NONE.name, UUIDv7.Empty)

        val ActionOptions = listOf(
            DropdownOption(EndAction.NONE.name, "Nothing"),
            DropdownOption(EndAction.CLOSE_SUBMITTING.name, "Close submitting for a compo"),
            DropdownOption(EndAction.CLOSE_VOTING_ALL.name, "Close voting for all compos"),
        )
    }
}

data class TimerMessage(
    @Field("Message")
    @Large
    val message: String,
) : Validateable<TimerMessage>
