package party.jml.partyboi.users

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.*
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.switchApi
import party.jml.partyboi.form.Form
import party.jml.partyboi.messages.MessageType
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondAndCatchEither
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.voting.VoteKey

fun Application.configureUserMgmtRouting(app: AppServices) {
    suspend fun renderUsersPage() = either {
        val users = app.users.getUsers().bind().sortedBy { it.name.lowercase() }
        UserListPage.render(users)
    }

    suspend fun renderEditPage(
        session: AppResult<User>,
        id: AppResult<Int>,
        currentForm: Form<UserCredentials>? = null,
    ) = either {
        val self = session.bind()
        val user = app.users.getUser(id.bind()).bind()
        val form = currentForm ?: Form(
            UserCredentials::class,
            UserCredentials.fromUser(user),
            initial = false
        )
        val voteKeys = app.voteKeys.getUserVoteKeys(user.id).bind()
        UserEditPage.render(
            session = self,
            user = user,
            credentials = form,
            voteKeys = voteKeys,
        )
    }

    adminRouting {
        get("/admin/users") {
            call.respondEither { renderUsersPage().bind() }
        }

        get("/admin/users/{id}") {
            call.respondEither {
                renderEditPage(
                    call.userSession(app),
                    call.parameterInt("id"),
                ).bind()
            }
        }

        get("/admin/users/{id}/send-verification") {
            call.respondAndCatchEither({
                val userId = call.parameterInt("id").bind()
                val user = app.users.getUser(userId).bind()
                app.users.sendVerificationEmail(user)?.bind()

                app.messages.sendMessage(
                    userId = call.userSession(app).bind().id,
                    type = MessageType.SUCCESS,
                    text = "Verification email sent to ${user.email}"
                ).bind()
                Redirection("/admin/users/$userId")
            }, {
                val userId = call.parameterString("id")
                app.messages.sendMessage(
                    userId = call.userSession(app).bind().id,
                    type = MessageType.ERROR,
                    text = "Sending verification email failed"
                ).bind()
                Redirection("/admin/users/$userId")
            })
        }

        post("/admin/users/{id}") {
            call.processForm<UserCredentials>(
                { credentials ->
                    val userId = call.parameterInt("id").bind()
                    app.users.updateUser(userId, credentials).bind()
                    Redirection("/admin/users/$userId")
                },
                {
                    renderEditPage(
                        session = call.userSession(app),
                        id = call.parameterInt("id"),
                        currentForm = it
                    ).bind()
                }
            )
        }
    }

    adminApiRouting {
        put("/admin/users/{id}/voting/{state}") {
            call.switchApi { id, state ->
                (if (state) {
                    app.voteKeys.insertVoteKey(id, VoteKey.manual(id), null)
                } else {
                    app.voteKeys.revokeUserVoteKeys(id)
                }).onRight {
                    app.users.requestUserSessionReload(id)
                }
            }
        }

        put("/admin/users/{id}/admin/{state}") {
            call.switchApi { id, state ->
                app.users.makeAdmin(id, state).onRight {
                    app.users.requestUserSessionReload(id)
                }
            }
        }
    }
}