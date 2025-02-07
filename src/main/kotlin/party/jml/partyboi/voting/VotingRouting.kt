package party.jml.partyboi.voting

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.*
import party.jml.partyboi.data.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

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

    userRouting {
        get("/vote/register") {
            call.respondPage(
                RegisterVoteKeyPage.render(Form.empty(VoteKeyForm.Empty))
            )
        }

        post("/vote/register") {
            call.processForm<VoteKeyForm>(
                { data ->
                    either {
                        val user = call.userSession().bind()
                        if (user.votingEnabled) raise(Unauthorized("You cannot register a vote key because you have voting rights already enabled."))

                        app.voteKeys
                            .registerTicket(user.id, data.code)
                            .mapLeft {
                                when (it) {
                                    is NotFound -> ValidationError(
                                        "code",
                                        "The code is invalid or already used",
                                        data.code
                                    )

                                    else -> it
                                }
                            }.bind()

                        call.sessions.set(user.copy(votingEnabled = true))

                        Redirection("/vote")
                    }
                },
                { RegisterVoteKeyPage.render(it).right() }
            )
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