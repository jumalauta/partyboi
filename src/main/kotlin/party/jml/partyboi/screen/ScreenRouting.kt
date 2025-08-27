package party.jml.partyboi.screen

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.templates.HtmlString
import party.jml.partyboi.templates.JsonResponse
import party.jml.partyboi.templates.longPolling
import party.jml.partyboi.templates.respondEither

fun Application.configureScreenRouting(app: AppServices) {
    routing {
        get("/screen") {
            val page = HtmlString(ScreenPage.render(app.screen.currentSlide(), app))
            call.respondEither { page }
        }

        get("/screen/next") {
            longPolling(app.screen.waitForNext()) { screen ->
                call.response.headers.append("X-SlideId", screen.id.toString())
                call.respondText(ScreenPage.renderContent(screen.slide, app), ContentType.Text.Html)
            }
        }

        get("/screen/examples") {
            call.respondEither { JsonResponse.from(getRenderedExampleSlides(app)) }
        }
    }
}