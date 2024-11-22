package party.jml.partyboi.schedule

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.publicRouting
import party.jml.partyboi.templates.respondEither

fun Application.configureScheduleRouting(app: AppServices) {
    publicRouting {
        get("/schedule") {
            call.respondEither({
                either {
                    val events = app.events.getPublic().bind()
                    SchedulePage.render(events)
                }
            })
        }
    }
}