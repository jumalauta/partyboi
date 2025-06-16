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
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.switchApi
import party.jml.partyboi.form.Form
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.triggers.NewScheduledTrigger

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
        eventId: AppResult<Int>,
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
        fun redirectionToEvent(id: Int) = Redirection("/admin/schedule/events/$id")

        get("/admin/schedule") {
            call.respondEither({ renderSchedulesPage(timeZone = app.time.timeZone.get().getOrNull()!!) })
        }

        post("/admin/schedule/events") {
            call.processForm<NewEvent>(
                { app.events.add(it).map { redirectionToSchedules } },
                {
                    renderSchedulesPage(
                        newEventForm = it,
                        timeZone = app.time.timeZone.get().getOrNull()!!
                    )
                }
            )
        }

        get("/admin/schedule/events/{id}") {
            call.respondEither({ renderEditSchedulePage(call.parameterInt("id")) })
        }

        post("/admin/schedule/events/{id}") {
            call.processForm<Event>(
                { app.events.update(it).map { redirectionToSchedules } },
                { renderEditSchedulePage(call.parameterInt("id"), eventForm = it) }
            )
        }

        post("/admin/schedule/triggers") {
            call.processForm<NewScheduledTrigger>(
                { t -> app.triggers.add(t.signal(), t.toAction()).map { redirectionToEvent(t.eventId) } },
                { renderEditSchedulePage(it.data.eventId.right(), newTriggerForm = it) }
            )
        }

    }

    adminApiRouting {
        delete("/admin/schedule/events/{id}") {
            call.apiRespond {
                either {
                    call.userSession(app).bind()
                    val eventId = call.parameterInt("id").bind()
                    app.events.delete(eventId).bind()
                }
            }
        }

        put("/admin/schedule/triggers/{id}/setEnabled/{state}") {
            call.switchApi { id, state -> app.triggers.setEnabled(id, state) }
        }
    }
}