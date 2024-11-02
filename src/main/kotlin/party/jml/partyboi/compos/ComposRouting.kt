package party.jml.partyboi.compos

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.optionalUserSession
import party.jml.partyboi.templates.respondEither

fun Application.configureComposRouting(app: AppServices) {
    routing {
        authenticate("user", optional = true) {
            get("/compos") {
                call.respondEither({
                    either {
                        val generalRules = app.compos.getGeneralRules().bind()
                        val compos = app.compos.getAllCompos().bind()
                        ComposPage.render(generalRules, compos)
                    }
                })
            }

            get("/results") {
                call.respondEither({
                    either {
                        val user = call.optionalUserSession()
                        val results = app.votes.getResultsForUser(user).bind()
                        ResultsPage.render(results)
                    }
                })
            }
        }
    }
}