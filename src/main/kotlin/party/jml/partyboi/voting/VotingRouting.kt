package party.jml.partyboi.voting

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.userApiRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.auth.votingRouting
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.templates.respondEither

fun Application.configureVotingRouting(app: AppServices) {
    votingRouting {
        get("/vote") {
            val user = call.userSession()
            call.respondEither({
                either {
                    val entries = app.votes.getVotableEntries(user.bind().id).bind()
                    UserVotingPage.render(
                        entries = entries,
                        screenshots = app.screenshots.getForEntries(entries),
                        liveVote = app.votes.getLiveVoteState(),
                    )
                }
            })
        }
    }

    userApiRouting {
        put("/vote/{entry}/{points}") {
            call.apiRespond {
                either {
                    val user = call.userSession().bind()
                    val entryId = call.parameterInt("entry").bind()
                    val points = call.parameterInt("points").bind()

                    app.votes.castVote(user, entryId, points).bind()
                }
            }
        }
    }
}