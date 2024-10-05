package party.jml.partyboi.compos

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.templates.respondEither
import arrow.core.raise.either
import io.ktor.server.auth.*

fun Application.configureComposRouting(app: AppServices) {
    routing {
        authenticate("user", optional = true) {
            get("/compos") {
                call.respondEither({
                    either {
                        val compos = app.compos.getAllCompos().bind()
                        ComposPage.render(compos)
                    }
                })
            }
        }
    }
}