package party.jml.partyboi.auth

import arrow.core.Option
import kotlinx.html.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page

object LoginPage {
    fun render(formData: Form<UserLogin> = Form(UserLogin::class, UserLogin.Empty, true)) =
        Page("Login") {
            renderForm(
                url = "/login",
                form = formData,
                title = "Login",
                submitButtonLabel = "Login"
            )
        }

    data class UserLogin(
        @property:Field(1, "User name")
        val name: String,
        @property:Field(2, "Password", presentation = FieldPresentation.secret)
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