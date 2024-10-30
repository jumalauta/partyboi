package party.jml.partyboi.auth

import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.templates.Page

object RegistrationPage {
    fun render(formData: Form<NewUser> = Form(NewUser::class, NewUser.Empty, true)) =
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