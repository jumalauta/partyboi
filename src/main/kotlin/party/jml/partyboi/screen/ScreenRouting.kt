package party.jml.partyboi.screen

import arrow.core.getOrElse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.Screen
import party.jml.partyboi.data.parameterString

fun Application.configureScreenRouting(app: AppServices) {
    routing {
        get("/screen") {
            call.respondText(ScreenPage.render(app.screen.current()), ContentType.Text.Html)
        }

        get("/screen/next") {
            app.screen.next().collect { screen ->
                call.respondText(ScreenPage.renderContent(screen), ContentType.Text.Html)
            }
        }

        get("/screen/test/{value}") {
            val value = call.parameterString("value").getOrElse { "error" }
            app.screen.show(Screen(value.capitalize(), "Just in: $value"))
            call.respondText(value)
        }
    }
}