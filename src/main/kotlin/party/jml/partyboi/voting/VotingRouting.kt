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
                either {
                    val entryId = catchError { call.parameters["entry"]?.toInt() ?: -1 }.bind()
                    val points = catchError { call.parameters["points"]?.toInt() ?: -1 }.bind()
                    val user = call.userSession()
                    app.votes.castVote(user.bind().id, entryId, points).bind()
                    call.respondText("OK")
                }.mapLeft {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }
}