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
import party.jml.partyboi.email.EmailTemplates
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureLoginRouting(app: AppServices) {
    routing {
        get("/login") {
            call.respondPage(LoginPage.render(emailServiceConfigured = app.email.isConfigured()))
        }

        post("/login") {
            call.sessions.clear<User>()

            call.processForm<LoginPage.UserLogin>(
                { login ->
                    either {
                        val user = app.users.getUserByName(login.name).bind()
                        val session = user.authenticate(login.password).bind()
                        call.sessions.set(session)
                        val redirect = call.request.cookies.get("afterLogin") ?: "/"
                        call.response.cookies.append("afterLogin", "", expires = GMTDate.START)
                        Redirection(redirect)
                    }
                },
                { form ->
                    LoginPage.render(
                        formData = form.mapError {
                            when (it) {
                                is NotFound -> Notice("Invalid user name or password")
                                else -> it
                            }
                        },
                        emailServiceConfigured = app.email.isConfigured()
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

        if (app.email.isConfigured()) {
            get("/reset-password") {
                val form = Form(PasswordResetForm::class, PasswordResetForm.Empty, true)
                call.respondPage(PasswordResetPage.render(form))
            }

            post("/reset-password") {
                call.processForm<PasswordResetForm>(
                    { form ->
                        either {
                            val resetCode = app.users.generatePasswordResetCode(form.email).bind()
                            app.email.sendMail(
                                EmailTemplates.passwordReset(
                                    recipient = form.email,
                                    resetCode = resetCode,
                                    instanceName = app.config.instanceName,
                                    hostName = app.config.hostName
                                )
                            ).bind()
                            Redirection("/reset-password-sent")
                        }
                    }, {
                        Redirection("/reset-password-sent").right()
                    }
                )
            }

            get("/reset-password-sent") {
                call.respondPage(PasswordResetPage.emailSent())
            }

            get("/reset-password/{resetCode}") {
                call.respondEither(
                    {
                        either {
                            val code = call.parameterString("resetCode").bind()
                            app.users.verifyPasswordResetCode(code).bind()
                            val form = Form(NewPasswordForm::class, NewPasswordForm.empty(code), true)
                            PasswordResetPage.passwordReset(form)
                        }
                    }, {
                        val form = Form(PasswordResetForm::class, PasswordResetForm.Empty, true)
                        PasswordResetPage.render(
                            form = form,
                            errorMsg = "The password reset code is invalid or expired. Try again."
                        ).right()
                    }
                )
            }

            post("/reset-password/change") {
                call.processForm<NewPasswordForm>(
                    {
                        either {
                            val userId = app.users.resetPassword(it.code, it.password).bind()
                            if (call.sessions.get<User>() == null) {
                                val user = app.users.getUser(userId).bind()
                                call.sessions.set(user)
                            }
                            Redirection("/")
                        }

                    },
                    {
                        PasswordResetPage.passwordReset(it).right()
                    }
                )
            }
        }

        get("/logout") {
            call.sessions.clear<User>()
            call.respondRedirect("/")
        }
    }
}

