package party.jml.partyboi.auth

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.date.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.*
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureLoginRouting(app: AppServices) {
    routing {
        get("/login") {
            call.respondPage(LoginPage.render())
        }

        post("/login") {
            call.sessions.clear<User>()

            call.processForm<LoginPage.UserLogin>(
                { login ->
                    either {
                        val user = app.users.getUser(login.name).bind()
                        val session = user.authenticate(login.password).bind()
                        call.sessions.set(session)
                        val redirect = call.request.cookies.get("afterLogin") ?: "/"
                        call.response.cookies.append("afterLogin", "", expires = GMTDate.START)
                        Redirection(redirect)

                    }
                },
                { form ->
                    LoginPage.render(
                        form.mapError {
                            when (it) {
                                is NotFound -> Notice("Invalid user name or password")
                                else -> it
                            }
                        }
                    ).right()
                }
            )
        }

        get("/register") {
            call.respondPage(RegistrationPage.render())
        }

        post("/register") {
            call.processForm<UserCredentials>(
                { newUser ->
                    either {
                        val session = app.users.addUser(newUser, call.request.origin.remoteAddress).bind()
                        call.sessions.set(session)
                        Redirection("/entries")
                    }
                },
                { form ->
                    RegistrationPage.render(
                        form.mapError {
                            if (it.message.contains("duplicate key value")) {
                                Notice("The user name or email has already been registered")
                            } else {
                                it
                            }
                        }
                    ).right()
                }
            )
        }

        get("/verify/{userId}/{verificationCode}") {
            call.respondEither({
                either {
                    val userId = call.parameterInt("userId").bind()
                    val verificationCode = call.parameterString("verificationCode").bind()
                    app.users.verifyEmail(userId, verificationCode)
                    Redirection("/")
                }
            })
        }

        get("/logout") {
            call.sessions.clear<User>()
            call.respondRedirect("/")
        }
    }
}

