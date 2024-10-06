package party.jml.partyboi.auth

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureLoginRouting(app: AppServices) {
    routing {
        get("/login") {
            call.respondPage(LoginPage.render())
        }

        post("/login") {
            call.sessions.clear<User>()
            val loginRequest = Form.fromParameters<LoginPage.UserLogin>(call.receiveMultipart())

            call.respondEither({ either {
                val login = loginRequest.bind().validated().bind()
                val user = app.users.getUser(login.name).bind()
                val session = user.authenticate(login.password).bind()
                call.sessions.set(session)
                RedirectPage("/entries")
            } }, { error -> either {
                LoginPage.render(loginRequest.bind().with(error))
            } })
        }

        get("/register") {
            call.respondPage(RegistrationPage.render())
        }

        post("/register") {
            val registrationRequest = Form.fromParameters<NewUser>(call.receiveMultipart())

            call.respondEither({ either {
                val registration = registrationRequest.bind()
                val newUser = registration.validated().bind()
                val session = app.users.addUser(newUser).bind()
                call.sessions.set(session)
                RedirectPage("/entries")
            } }, { error -> either {
                RegistrationPage.render(registrationRequest.bind().with(error))
            } })
        }

        get("/logout") {
            call.sessions.clear<User>()
            call.respondRedirect("/")
        }
    }
}

