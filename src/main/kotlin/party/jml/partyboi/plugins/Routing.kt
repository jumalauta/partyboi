package party.jml.partyboi.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.h1
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.respondPage

fun Application.configureDefaultRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }
    install(AutoHeadResponse)

    routing {
        authenticate("user", optional = true) {
            get("/") {
                call.respondPage(Page("Home") {
                    h1 { +"Hello, world!" }
                })
            }
        }
    }
}
