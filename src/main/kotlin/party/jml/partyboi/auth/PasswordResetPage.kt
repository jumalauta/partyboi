package party.jml.partyboi.auth

import kotlinx.html.article
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page

object PasswordResetPage {
    fun render(form: Form<PasswordResetForm>, errorMsg: String? = null) = Page("Password reset") {
        errorMsg?.let { article(classes = "error") { +it } }
        renderForm(
            url = "/reset-password",
            form = form,
            title = "Reset password",
            submitButtonLabel = "Send reset link",
        )
    }

    fun emailSent() = Page("Password reset") {
        article {
            +"A password reset email is on its way. Check your inbox."
        }
    }

    fun passwordReset(form: Form<NewPasswordForm>) = Page("Password reset") {
        renderForm(
            url = "/reset-password/change",
            form = form,
            title = "Reset password",
            submitButtonLabel = "Change password",
        )
    }
}