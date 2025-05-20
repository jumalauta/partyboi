package party.jml.partyboi.screen

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.NotFound
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.templates.HtmlString
import party.jml.partyboi.templates.respondEither

fun Application.configureScreenRouting(app: AppServices) {
    routing {
        get("/screen") {
            val defaultTheme = ScreenTheme.entries.first()
            call.respondRedirect("/screen/${defaultTheme.dir}")
        }

        get("/screen/{theme}") {
            val page = call.parameterString("theme").getOrNone()
                .flatMap { ScreenTheme.getTheme(it) }
                .toEither { NotFound("Theme not found") }
                .map { ScreenPage.render(app.screen.currentSlide(), it, app) }
                .map { HtmlString(it) }
            call.respondEither({ page })
        }

        get("/screen/next") {
            app.screen.waitForNext().collect { screen ->
                call.response.headers.append("X-SlideId", screen.id.toString())
                call.respondText(ScreenPage.renderContent(screen.slide, app), ContentType.Text.Html)
            }
        }
    }
}