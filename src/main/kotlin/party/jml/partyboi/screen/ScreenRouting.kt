package party.jml.partyboi.screen

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices

fun Application.configureScreenRouting(app: AppServices) {
    routing {
        get("/screen") {
            call.respondText(ScreenPage.render(app.screen.currentSlide()), ContentType.Text.Html)
        }

        get("/screen/next") {
            app.screen.waitForNext().collect { screen ->
                call.respondText(ScreenPage.renderContent(screen), ContentType.Text.Html)
            }
        }
    }
}