package party.jml.partyboi.voting.admin

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.templates.respondEither

fun Application.configureAdminVotingRouting(app: AppServices) {
    adminRouting {
        get("/admin/voting") {
            call.respondEither({
                either {
                    val voteKeys = app.voteKeys.getAllVoteKeys().bind()
                    val users = app.users.getUsers().bind()
                    AdminVotingPage.render(voteKeys, users)
                }
            })
        }
    }
}