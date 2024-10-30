package party.jml.partyboi.admin.schedule

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.receiveForm
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.data.switchApi
import party.jml.partyboi.form.Form
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.templates.EmptyPage
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.triggers.NewScheduledTrigger

fun Application.configureAdminScheduleRouting(app: AppServices) {
    routing {
        authenticate("admin") {
            get("/admin/schedule") {
                call.respondEither({ either {
                    val events = app.events.getAll().bind()
                    val newEvent = Form(NewEvent::class, NewEvent.make(events), true)
                    AdminSchedulePage.render(events, newEvent)
                } })
            }

            get("/admin/schedule/events/{id}") {
                call.respondEither({ either {
                    val eventId = call.parameterInt("id").bind()
                    val event = app.events.get(eventId).bind()
                    val eventForm = Form(Event::class, event, true)
                    val triggers = app.triggers.getTriggersForEvent(eventId).bind()
                    val compos = app.compos.getAllCompos().bind()
                    val newTriggerForm = Form(NewScheduledTrigger::class, NewScheduledTrigger.empty(eventId), initial = true)
                    AdminSchedulePage.renderEdit(eventForm, triggers, compos, newTriggerForm)
                } })
            }

            post("/admin/schedule/events") {
                val newEvent = Form.fromParameters<NewEvent>(call.receiveMultipart())
                call.respondEither({ either {
                    val event = newEvent.bind().validated().bind()
                    app.events.add(event).bind()
                    RedirectPage("/admin/schedule")
                }}, { error -> either {
                    val events = app.events.getAll().bind()
                    AdminSchedulePage.render(events, newEvent.bind().with(error))
                } })
            }

            post("/admin/schedule/events/{id}") {
                val eventReq = Form.fromParameters<Event>(call.receiveMultipart())
                call.respondEither({ either {
                    val event = eventReq.bind().validated().bind()
                    app.events.update(event).bind()
                    RedirectPage("/admin/schedule")
                }}, { error -> either {
                    val event = eventReq.bind().with(error)
                    val compos = app.compos.getAllCompos().bind()
                    val newTriggerForm = Form(NewScheduledTrigger::class, NewScheduledTrigger.empty(event.data.id), initial = true)
                    AdminSchedulePage.renderEdit(event, emptyList(), compos, newTriggerForm)
                } })
            }

            post("/admin/schedule/triggers") {
                val newTriggerReq = call.receiveForm<NewScheduledTrigger>()
                call.respondEither({ either {
                    val newTrigger = newTriggerReq.bind().validated().bind()
                    app.triggers.schedule(newTrigger.eventId, newTrigger.toTrigger()).bind()
                    RedirectPage("/admin/schedule/events/${newTrigger.eventId}")
                } }, { error -> either {
                    val eventId = newTriggerReq.bind().data.eventId
                    val event = app.events.get(eventId).bind()
                    val eventForm = Form(Event::class, event, true)
                    val triggers = app.triggers.getTriggersForEvent(eventId).bind()
                    val compos = app.compos.getAllCompos().bind()
                    AdminSchedulePage.renderEdit(eventForm, triggers, compos, newTriggerReq.bind().with(error))
                } })
            }
        }

        authenticate("admin", optional = true) {
            delete("/admin/schedule/events/{id}") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val eventId = call.parameterInt("id").bind()
                    app.events.delete(eventId).bind()
                }}
            }

            put("/admin/schedule/triggers/{id}/setEnabled/{state}") {
                call.switchApi { id, state -> app.triggers.setEnabled(id, state) }
            }
        }
    }
}