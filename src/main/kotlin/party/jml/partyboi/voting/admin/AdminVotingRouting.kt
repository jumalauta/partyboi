package party.jml.partyboi.voting.admin

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.settings.VoteSettings
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureAdminVotingRouting(app: AppServices) {
    adminRouting {
        get("/admin/voting") {
            call.respondEither {
                val voteKeys = app.voteKeys.getAllVoteKeys().bind()
                val users = app.users.getUsers().bind()
                val settings = Form(
                    VoteSettings::class,
                    app.settings.getVoteSettings().bind(),
                    initial = false
                )
                AdminVotingPage.render(voteKeys, users, settings)
            }
        }

        post("/admin/voting/settings") {
            call.processForm<VoteSettings>(
                { app.settings.saveSettings(it).map { Redirection("/admin/voting") }.bind() },
                { settings ->
                    val voteKeys = app.voteKeys.getAllVoteKeys().bind()
                    val users = app.users.getUsers().bind()
                    AdminVotingPage.render(voteKeys, users, settings)
                }
            )
        }

        get("/admin/voting/generate") {
            val form = Form(
                GenerateVoteKeysPage.GenerateVoteKeySettings::class,
                GenerateVoteKeysPage.GenerateVoteKeySettings.Empty,
                initial = true,
            )
            call.respondPage(GenerateVoteKeysPage.renderForm(form))
        }

        post("/admin/voting/generate") {
            call.processForm<GenerateVoteKeysPage.GenerateVoteKeySettings>(
                {
                    val keySet = app.time.isoLocalTime()
                    app.voteKeys.createTickets(it.numberOfKeys, keySet).bind()
                    Redirection("/admin/voting/print/$keySet")
                },
                { GenerateVoteKeysPage.renderForm(it) }
            )
        }

        get("/admin/voting/print/{set}") {
            call.respondEither {
                val keySet = call.parameterString("set").bind()
                val tickets = app.voteKeys.getVoteKeySet(keySet).bind()
                GenerateVoteKeysPage.renderTickets(tickets)
            }
        }
    }
}