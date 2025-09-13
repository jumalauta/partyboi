package party.jml.partyboi.schedule.admin

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterUUID
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.switchApi
import party.jml.partyboi.form.Form
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.triggers.NewScheduledTrigger
import java.util.*

fun Application.configureAdminScheduleRouting(app: AppServices) {

    suspend fun renderSchedulesPage(newEventForm: Form<NewEvent>? = null, timeZone: TimeZone) = either {
        val events = app.events.getAll().bind()
        AdminSchedulePage.render(
            newEventForm = newEventForm ?: Form(NewEvent::class, NewEvent.make(events, app.time), true),
            events = events,
            timeZone = timeZone
        )
    }

    suspend fun renderEditSchedulePage(
        eventId: AppResult<UUID>,
        eventForm: Form<Event>? = null,
        newTriggerForm: Form<NewScheduledTrigger>? = null,
    ) = either {
        val event = app.events.get(eventId.bind()).bind()
        AdminSchedulePage.renderEdit(
            eventForm = eventForm ?: Form(Event::class, event, true),
            newTriggerForm = newTriggerForm ?: Form(
                NewScheduledTrigger::class,
                NewScheduledTrigger.empty(eventId.bind()),
                initial = true
            ),
            triggers = app.triggers.getTriggersForSignal(event.signal()).bind(),
            compos = app.compos.getAllCompos().bind(),
        )
    }

    adminRouting {
        val redirectionToSchedules = Redirection("/admin/schedule")
        fun redirectionToEvent(id: UUID) = Redirection("/admin/schedule/events/$id")

        get("/admin/schedule") {
            call.respondEither { renderSchedulesPage(timeZone = app.time.timeZone.get().getOrNull()!!).bind() }
        }

        post("/admin/schedule/events") {
            call.processForm<NewEvent>(
                { app.events.add(it).map { redirectionToSchedules }.bind() },
                {
                    renderSchedulesPage(
                        newEventForm = it,
                        timeZone = app.time.timeZone.get().getOrNull()!!
                    ).bind()
                }
            )
        }

        get("/admin/schedule/events/{id}") {
            call.respondEither { renderEditSchedulePage(call.parameterUUID("id")).bind() }
        }

        post("/admin/schedule/events/{id}") {
            call.processForm<Event>(
                { app.events.update(it).map { redirectionToSchedules }.bind() },
                { renderEditSchedulePage(call.parameterUUID("id"), eventForm = it).bind() }
            )
        }

        post("/admin/schedule/triggers") {
            call.processForm<NewScheduledTrigger>(
                { t -> app.triggers.add(t.signal(), t.toAction()).map { redirectionToEvent(t.eventId) }.bind() },
                { renderEditSchedulePage(it.data.eventId.right(), newTriggerForm = it).bind() }
            )
        }

    }

    adminApiRouting {
        delete("/admin/schedule/events/{id}") {
            call.apiRespond {
                call.userSession(app).bind()
                val eventId = call.parameterUUID("id").bind()
                app.events.delete(eventId).bind()
            }
        }

        put("/admin/schedule/triggers/{id}/setEnabled/{state}") {
            call.switchApi { id, state -> app.triggers.setEnabled(id, state) }
        }
    }
}