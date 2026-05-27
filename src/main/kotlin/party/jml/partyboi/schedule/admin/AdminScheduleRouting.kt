package party.jml.partyboi.schedule.admin

import arrow.core.flattenOption
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.jsonRespond
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

@Serializable
data class CreatedEvent(val id: String)

// Validate only the start/end ordering of an event (not other field constraints like a
// required name), so inline time edits work on a not-yet-named scaffold row.
private fun Event.assertTimeOrder(): AppResult<Unit> =
    validationErrors().flattenOption().toNonEmptyListOrNull()
        ?.let { ValidationError(it).left() }
        ?: Unit.right()

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
                {
                    app.events.add(it).bind()
                    app.screen.syncScheduleSlides().bind()
                    redirectionToSchedules
                },
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
                {
                    app.events.update(it).bind()
                    app.screen.syncScheduleSlides().bind()
                    redirectionToSchedules
                },
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
                app.screen.syncScheduleSlides().bind()
            }
        }

        // Scaffold a blank event row (empty name, start time following the last event)
        // for keyboard-driven entry; returns its id so the client can focus the new row.
        post("/admin/schedule/events/new") {
            call.jsonRespond {
                call.userSession(app).bind()
                val events = app.events.getAll().bind()
                val created = app.events.add(NewEvent.make(events, app.time)).bind()
                app.screen.syncScheduleSlides().bind()
                CreatedEvent(created.id.toString())
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
        // event's existing date so the date can't be changed here by accident. Only the
        // start/end ordering is validated (not e.g. the name, which may still be blank
        // on a freshly scaffolded row being filled in).
        put("/admin/schedule/events/{id}/startTime") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                val time = call.receive<ValueUpdate>().value
                val event = app.events.get(id).bind()
                val startTime = event.startTime.withTimeOfDay(time)
                event.copy(startTime = startTime).assertTimeOrder().bind()
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
                event.copy(endTime = endTime).assertTimeOrder().bind()
                app.events.setEndTime(id, endTime).bind()
            }
        }

        // Toggling visibility changes which dates have a public event, so re-sync the
        // schedule slides (hiding the last public event on a date removes its slide).
        put("/admin/schedule/events/{id}/setVisible/{state}") {
            call.switchApiUuid { id, state ->
                either {
                    app.events.setVisible(id, state).bind()
                    app.screen.syncScheduleSlides().bind()
                }
            }
        }

        // Bump a single event by N minutes, preserving its duration.
        put("/admin/schedule/events/{id}/nudge/{minutes}") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                val minutes = call.parameterInt("minutes").bind()
                app.events.nudge(id, minutes.minutes).bind()
                app.screen.syncScheduleSlides().bind()
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
                app.screen.syncScheduleSlides().bind()
            }
        }

        put("/admin/schedule/triggers/{id}/setEnabled/{state}") {
            call.switchApiUuid { id, state -> app.triggers.setEnabled(id, state) }
        }
    }
}