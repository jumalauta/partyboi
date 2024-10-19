package party.jml.partyboi.screen

import arrow.core.toOption
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.parameterInt

fun Application.configureScreenRouting(app: AppServices) {
    routing {
        get("/screen") {
            call.respondText("<!DOCTYPE html>" + ScreenPage.render(app.screen.currentSlide()), ContentType.Text.Html)
        }

        get("/screen/next") {
            app.screen.waitForNext().collect { screen ->
                call.response.headers.append("X-SlideId", screen.id.toString())
                call.respondText(ScreenPage.renderContent(screen.slide), ContentType.Text.Html)
            }
        }

        get("/screen/next/{currentSlideId}") {
            val currentSlideId = call.parameterInt("currentSlideId").getOrNull()
            app.screen.waitForNext(currentSlideId).collect { screen ->
                call.response.headers.append("X-SlideId", screen.id.toString())
                call.respondText(ScreenPage.renderContent(screen.slide), ContentType.Text.Html)
            }
        }
    }
}