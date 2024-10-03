package party.jml.partyboi.compos

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.templates.respondEither
import arrow.core.raise.either

fun Application.configureComposRouting(app: AppServices) {
    routing {
        get("/compos") {
            call.respondEither({ either {
                val compos = app.compos.getAllCompos().bind()
                ComposPage.render(compos)
            }})
        }
    }
}