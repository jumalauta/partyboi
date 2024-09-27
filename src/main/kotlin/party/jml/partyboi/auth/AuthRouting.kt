package party.jml.partyboi.auth

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.database.DatabasePool
import party.jml.partyboi.database.NewUser
import party.jml.partyboi.database.UserRepository
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.Form
import party.jml.partyboi.submit.formTextInput
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureLoginRouting(db: DatabasePool) {
    val users = UserRepository(db)

    routing {
        get("/register") {
            call.respondPage(registrationPage())
        }

        post("/register") {
            val params = call.receiveMultipart()
            val formResult = Form.fromParameters<NewUser>(params)

            call.respondEither({ either { registrationPage(formResult.bind()) }}) {
                either {
                    val form = formResult.bind()
                    val newUser = form.validated().bind().copy(ipAddr = call.request.origin.remoteAddress)
                    users.addUser(newUser).bind()
                    RedirectPage("/submit")
                }
            }
        }
    }
}

fun registrationPage(formData: Form<NewUser> = Form(NewUser::class, NewUser.Empty, true)) = Page("Register") {
    form(classes = "submitForm appForm", method = FormMethod.post, action = "/register", encType = FormEncType.multipartFormData) {
        article {
            header { +"Register as a guest" }
            fieldSet {
                formData.forEach { label, key, v, error ->
                    formTextInput(label, key, error) { value = v }
                }
            }
            footer {
                submitInput { value = "Register" }
            }
        }
    }
}
