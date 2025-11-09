package party.jml.partyboi.compos

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.optionalUserSession
import party.jml.partyboi.auth.publicRouting
import party.jml.partyboi.templates.respondEither

fun Application.configureComposRouting(app: AppServices) {
    publicRouting {
        get("/compos") {
            call.respondEither {
                val generalRules = app.compos.generalRules.get().bind()
                val compos = app.compos.getAllCompos().bind()
                val defaultVotingSettings = app.settings.getVoteKeySettings().bind()
                ComposPage.render(generalRules, compos, defaultVotingSettings)
            }
        }

        get("/results") {
            call.respondEither {
                val user = call.optionalUserSession(app)
                val results = app.votes.getResultsForUser(user).bind()
                ResultsPage.render(results)
            }
        }
    }
}