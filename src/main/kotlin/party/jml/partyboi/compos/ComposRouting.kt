package party.jml.partyboi.compos

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices

fun Application.configureComposRouting(app: AppServices) {
    routing {
        authenticate("admin") {
            get("/compos") {
                call.respondText("TODO")
            }
        }
    }
}