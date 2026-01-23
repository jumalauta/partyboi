package party.jml.partyboi.schedule

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.publicRouting
import party.jml.partyboi.templates.respondEither

fun Application.configureScheduleRouting(app: AppServices) {
    publicRouting {
        get("/schedule") {
            call.respondEither {
                val events = app.events.getPublic().bind()
                SchedulePage.render(events)
            }
        }

        get("/schedule/json") {
            either {
                val events = app.events.getPublic().bind()
                call.respond(events)
            }
        }

        get("/schedule.ics") {
            either {
                val events = app.events.getPublic().bind()
                val ics = ICalendar.eventsToIcs(
                    hostname = app.config.hostName,
                    instanceName = app.config.instanceName,
                    events = events
                )
                call.response.headers.append(
                    HttpHeaders.ContentType,
                    "text/calendar; charset=utf-8"
                )
                call.respondText { ics }
            }
        }
    }
}