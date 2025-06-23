package party.jml.partyboi.auth

import kotlinx.html.article
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page

object PasswordResetPage {
    fun render(form: Form<PasswordResetForm>, errorMsg: String? = null) = Page("Password reset") {
        if (errorMsg != null) {
            article(classes = "error") { +errorMsg }
        }
        renderForm(
            url = "/reset-password",
            form = form,
            title = "Reset password",
            submitButtonLabel = "Request password reset link to email",
        )
    }

    fun emailSent() = Page("Password reset") {
        article {
            +"Password reset email has been sent to the given email address. Check your mail now."
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