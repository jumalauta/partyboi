package party.jml.partyboi.frontpage

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.publicRouting
import party.jml.partyboi.screen.SlideSetRow
import party.jml.partyboi.templates.respondEither

fun Application.configureFrontPageRouting(app: AppServices) {
    publicRouting {
        get("/") {
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