package party.jml.partyboi.auth

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import party.jml.partyboi.data.FormReader
import party.jml.partyboi.database.DatabasePool
import party.jml.partyboi.database.NewUser
import party.jml.partyboi.database.UserRepository
import party.jml.partyboi.errors.ValidationError
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
            val form = FormReader(call.receiveParameters())
            call.respondEither({ registrationPage(it).right() }) { either {
                val newUser = NewUser(
                    ipAddr = call.request.origin.remoteAddress,
                    name = form.string("name").bind(),
                ).validate().bind()
                users.addUser(newUser).bind()
                RedirectPage("/submit")
            } }
        }
    }
}

fun registrationPage(error: ValidationError? = null) = Page("Login") {
    h1 { +"Register" }
    form(classes = "submitForm appForm", method = FormMethod.post, action = "/register", encType = FormEncType.multipartFormData) {
        //formTextInput("User name", "name", error)//{ maxLength = "64" }
        +"TODO: Korjaa lomake"
        footer {
            submitInput { value = "Register" }
        }
    }
}