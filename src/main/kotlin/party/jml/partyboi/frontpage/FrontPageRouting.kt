package party.jml.partyboi.frontpage

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.optionalUserSession
import party.jml.partyboi.auth.publicRouting
import party.jml.partyboi.screen.SlideSetRow
import party.jml.partyboi.templates.respondEither

fun Application.configureFrontPageRouting(app: AppServices) {
    publicRouting {
        get("/") {
            call.optionalUserSession(app) // FIXME: this is a hack to ensure that new session gets loaded before rendering
            call.respondEither({
                either {
                    val events = app.events.getAll().bind().filter { it.visible }
                    val infoScreen = app.screen
                        .getSlideSet(SlideSetRow.DEFAULT).bind()
                        .filter { it.showOnInfoPage }
                    val timeZone = app.time.timeZone.get().bind()
                    FrontPage.render(events, infoScreen, timeZone)
                }
            })
        }
    }
}