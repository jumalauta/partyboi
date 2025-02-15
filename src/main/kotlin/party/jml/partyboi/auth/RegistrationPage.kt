package party.jml.partyboi.auth

import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page

object RegistrationPage {
    fun render(formData: Form<UserCredentials> = Form(UserCredentials::class, UserCredentials.Empty, true)) =
        Page("Register") {
            renderForm(
                title = "Register a new account",
                url = "/register",
                form = formData,
                submitButtonLabel = "Register"
            )
        }
}