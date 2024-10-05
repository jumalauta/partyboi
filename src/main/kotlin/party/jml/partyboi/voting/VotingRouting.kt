package party.jml.partyboi.voting

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.templates.respondEither
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.response.*
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.errors.catchError

fun Application.configureVotingRouting(app: AppServices) {
    routing {
        authenticate("user") {
            get("/vote") {
                val user = call.userSession()
                call.respondEither({ either {
                    val entries = app.entries.getVotableEntries(user.bind().id)
                    UserVotingPage.render(entries.bind())
                }})
            }
        }

        // API routes (we don't want to redirect user to login page)
        authenticate("user", optional = true) {
            put("/vote/{entry}/{points}") {
                call.apiRespond { either {
                    val user = call.userSession().bind()
                    val entryId = call.parameterInt("entry").bind()
                    val points = call.parameterInt("points").bind()

                    app.votes.castVote(user.id, entryId, points).bind()
                } }
            }
        }
    }
}