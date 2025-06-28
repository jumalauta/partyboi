package party.jml.partyboi.auth

import kotlinx.html.a
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.validation.MinLength
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable

object LoginPage {
    fun render(
        formData: Form<UserLogin> = Form(UserLogin::class, UserLogin.Empty, true),
        emailServiceConfigured: Boolean
    ) =
        Page("Login") {
            renderForm(
                url = "/login",
                form = formData,
                title = "Login",
                submitButtonLabel = "Login"
            )
            if (emailServiceConfigured) {
                a(href = "/reset-password") {
                    +"I forgot my password"
                }
            }
        }

    data class UserLogin(
        @Label("User name")
        @NotEmpty
        val name: String,
        @Label("Password")
        @Presentation(FieldPresentation.secret)
        @MinLength(8)
        val password: String
    ) : Validateable<UserLogin> {
        companion object {
            val Empty = UserLogin("", "")
        }
    }
}