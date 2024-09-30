package party.jml.partyboi.auth

import arrow.core.Option
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.database.DatabasePool
import party.jml.partyboi.database.NewUser
import party.jml.partyboi.database.User
import party.jml.partyboi.database.UserRepository
import party.jml.partyboi.errors.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.Form
import party.jml.partyboi.entries.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureLoginRouting(db: DatabasePool) {
    val users = UserRepository(db)

    routing {
        get("/login") {
            call.respondPage(loginPage())
        }

        post("/login") {
            val loginRequest = Form.fromParameters<UserLogin>(call.receiveMultipart())

            call.respondEither({ either { loginPage(loginRequest.bind()) } }) {
                either {
                    val login = loginRequest.bind().validated().bind()
                    val user = users.getUser(login.name).bind()
                    val session = user.authenticate(login.password).bind()
                    call.sessions.set(session)
                    RedirectPage("/entries")
                }
            }
        }

        get("/register") {
            call.respondPage(registrationPage())
        }

        post("/register") {
            val registrationRequest = Form.fromParameters<NewUser>(call.receiveMultipart())

            call.respondEither({ either { registrationPage(registrationRequest.bind()) }}) {
                either {
                    val registration = registrationRequest.bind()
                    val newUser = registration.validated().bind()
                    val session = users.addUser(newUser).bind()
                    call.sessions.set(session)
                    RedirectPage("/entries")
                }
            }
        }

        get("/logout") {
            call.sessions.clear<User>()
            call.respondRedirect("/")
        }
    }
}

fun registrationPage(formData: Form<NewUser> = Form(NewUser::class, NewUser.Empty, true)) = Page("Register") {
    form(method = FormMethod.post, action = "/register", encType = FormEncType.multipartFormData) {
        article {
            header { +"Register a new account" }
            fieldSet {
                renderForm(formData)
            }
            footer {
                submitInput { value = "Register" }
            }
        }
    }
}

fun loginPage(formData: Form<UserLogin> = Form(UserLogin::class, UserLogin.Empty, true)) = Page("Login") {
    form(method = FormMethod.post, action = "/login", encType = FormEncType.multipartFormData) {
        article {
            header { +"Login" }
            fieldSet {
                renderForm(formData)
            }
            footer {
                submitInput { value = "Login" }
            }
        }
    }
}

data class UserLogin(
    @property:Field(1, "User name")
    val name: String,
    @property:Field(2, "Password", type = InputType.password)
    val password: String
) : Validateable<UserLogin> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMinLength("password", password, 8),
    )

    companion object {
        val Empty = UserLogin("", "")
    }
}
