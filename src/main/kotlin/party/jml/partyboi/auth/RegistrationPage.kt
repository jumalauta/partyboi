package party.jml.partyboi.auth

import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.templates.Page

object RegistrationPage {
    fun render(formData: Form<UserCredentials> = Form(UserCredentials::class, UserCredentials.Empty, true)) =
        Page("Register") {
            dataForm("/register") {
                article {
                    header { +"Register a new account" }
                    fieldSet {
                        renderFields(formData)
                    }
                    footer {
                        submitInput { value = "Register" }
                    }
                }
            }
        }
}