package party.jml.partyboi.auth

import kotlinx.html.*
import party.jml.partyboi.database.NewUser
import party.jml.partyboi.entries.renderForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Page

object RegistrationPage {
    fun render(formData: Form<NewUser> = Form(NewUser::class, NewUser.Empty, true)) =
        Page("Register") {
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
}