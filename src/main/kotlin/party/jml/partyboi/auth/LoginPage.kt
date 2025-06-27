package party.jml.partyboi.auth

import arrow.core.Option
import kotlinx.html.a
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page

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
        val name: String,
        @Label("Password")
        @Presentation(FieldPresentation.secret)
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
}