package party.jml.partyboi.admin.users

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.*
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.switchApi
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.voting.VoteKey

fun Application.configureUserMgmtRouting(app: AppServices) {
    fun renderUsersPage() = either {
        val users = app.users.getUsers().bind().sortedBy { it.name.lowercase() }
        UserListPage.render(users)
    }

    fun renderEditPage(
        session: Either<AppError, User>,
        id: Either<AppError, Int>,
        currentForm: Form<UserCredentials>? = null
    ) = either {
        val self = session.bind()
        val user = app.users.getUser(id.bind()).bind()
        val form = currentForm ?: Form(
            UserCredentials::class,
            UserCredentials.fromUser(user),
            initial = false
        )
        val voteKeys = app.voteKeys.getUserVoteKeys(user.id).bind()
        UserEditPage.render(self, user, form, voteKeys)
    }

    adminRouting {
        get("/admin/users") {
            call.respondEither({ renderUsersPage() })
        }

        get("/admin/users/{id}") {
            call.respondEither({
                renderEditPage(
                    call.userSession(),
                    call.parameterInt("id")
                )
            })
        }

        post("/admin/users/{id}") {
            call.processForm<UserCredentials>(
                { credentials ->
                    either {
                        val userId = call.parameterInt("id").bind()
                        app.users.updateUser(userId, credentials).bind()
                        Redirection("/admin/users/$userId")
                    }
                },
                {
                    renderEditPage(
                        session = call.userSession(),
                        id = call.parameterInt("id"),
                        currentForm = it
                    )
                }
            )
        }
    }

    adminApiRouting {
        put("/admin/users/{id}/voting/{state}") {
            call.switchApi { id, state ->
                val admin = call.userSession().getOrNull()
                if (state) {
                    app.voteKeys.insertVoteKey(id, VoteKey.manual(admin?.name ?: "anon"))
                } else {
                    app.voteKeys.revokeUserVoteKeys(id)
                }
            }
        }

        put("/admin/users/{id}/admin/{state}") {
            call.switchApi { id, state -> app.users.makeAdmin(id, state) }
        }
    }
}