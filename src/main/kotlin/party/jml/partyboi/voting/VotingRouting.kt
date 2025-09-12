package party.jml.partyboi.voting

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.userApiRouting
import party.jml.partyboi.auth.userRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.auth.votingRouting
import party.jml.partyboi.data.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureVotingRouting(app: AppServices) {
    votingRouting {
        get("/vote") {
            val user = call.userSession(app)
            call.respondEither {
                val entries = app.votes.getVotableEntries(user.bind().id).bind()
                UserVotingPage.render(
                    entries = entries,
                    screenshots = app.screenshots.getForEntries(entries),
                    liveVote = app.votes.getLiveVoteState(),
                )
            }
        }
    }

    userRouting {
        get("/vote/register") {
            call.respondPage(
                RegisterVoteKeyPage.render(Form.of(VoteKeyForm.Empty))
            )
        }

        post("/vote/register") {
            call.processForm<VoteKeyForm>(
                { data ->
                    val user = call.userSession(app).bind()
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
                },
                { RegisterVoteKeyPage.render(it) }
            )
        }
    }

    userApiRouting {
        put("/vote/{entry}/{points}") {
            call.apiRespond {
                val user = call.userSession(app).bind()
                val entryId = call.parameterUUID("entry").bind()
                val points = call.parameterInt("points").bind()

                app.votes.castVote(user, entryId, points).bind()
            }
        }
    }
}