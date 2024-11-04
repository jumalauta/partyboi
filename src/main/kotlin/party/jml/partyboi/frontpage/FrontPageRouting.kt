package party.jml.partyboi.frontpage

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.templates.respondEither

fun Application.configureFrontPageRouting(app: AppServices) {
    routing {
        authenticate("user", optional = true) {
            get("/") {
                call.respondEither({
                    either {
                        val events = app.events.getAll().bind().filter { it.visible }
                        val infoScreen = app.screen.getSlideSet("rotation").bind().filter { it.visible }
                        FrontPage.render(events, infoScreen)
                    }
                })
            }
        }
    }
}