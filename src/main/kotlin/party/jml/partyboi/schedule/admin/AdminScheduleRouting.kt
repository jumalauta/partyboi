package party.jml.partyboi.schedule.admin

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.data.parameterUUID
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.switchApiUuid
import party.jml.partyboi.form.Form
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.withTimeOfDay
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.triggers.NewScheduledTrigger
import java.util.*
import kotlin.time.Duration.Companion.minutes

@Serializable
data class ValueUpdate(val value: String)

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

        // Inline editing: each editable cell PUTs its single value.
        put("/admin/schedule/events/{id}/name") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                val value = call.receive<ValueUpdate>().value
                app.events.setName(id, value).bind()
            }
        }

        // Inline time edits are time-only: combine the submitted time of day with the
        // event's existing date so the date can't be changed here by accident.
        put("/admin/schedule/events/{id}/startTime") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                val time = call.receive<ValueUpdate>().value
                val event = app.events.get(id).bind()
                val startTime = event.startTime.withTimeOfDay(time)
                event.copy(startTime = startTime).validate(Event::class).bind()
                app.events.setStartTime(id, startTime).bind()
            }
        }

        put("/admin/schedule/events/{id}/endTime") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                val raw = call.receive<ValueUpdate>().value
                val event = app.events.get(id).bind()
                val endTime = if (raw.isBlank()) null else (event.endTime ?: event.startTime).withTimeOfDay(raw)
                event.copy(endTime = endTime).validate(Event::class).bind()
                app.events.setEndTime(id, endTime).bind()
            }
        }

        put("/admin/schedule/events/{id}/setVisible/{state}") {
            call.switchApiUuid { id, state -> app.events.setVisible(id, state) }
        }

        // Bump a single event by N minutes, preserving its duration.
        put("/admin/schedule/events/{id}/nudge/{minutes}") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                val minutes = call.parameterInt("minutes").bind()
                app.events.nudge(id, minutes.minutes).bind()
            }
        }

        // "Running late": shift the given event and every later one by N minutes.
        put("/admin/schedule/shift/{id}/{minutes}") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                val minutes = call.parameterInt("minutes").bind()
                val event = app.events.get(id).bind()
                app.events.shiftFrom(event.startTime, minutes.minutes).bind()
            }
        }

        put("/admin/schedule/triggers/{id}/setEnabled/{state}") {
            call.switchApiUuid { id, state -> app.triggers.setEnabled(id, state) }
        }
    }
}