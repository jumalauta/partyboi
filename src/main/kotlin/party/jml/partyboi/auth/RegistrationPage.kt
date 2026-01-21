package party.jml.partyboi.auth

import kotlinx.html.article
import kotlinx.html.fieldSet
import kotlinx.html.header
import party.jml.partyboi.RecaptchaConfig
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.templates.Page

object RegistrationPage {
    fun render(
        formData: Form<UserCredentials> = Form(UserCredentials::class, UserCredentials.Empty, true),
        recaptcha: RecaptchaConfig?,
    ) =
        Page("Register") {
            dataForm("/register") {
                attributes["id"] = "register"
                article {
                    header { +"Register a new account" }
                    fieldSet { renderFields(formData) }
                    recaptcha
                        ?.let { recaptchaSubmitButton("register", "Register", it) }
                        ?: submitButton("Register")
                }
            }
        }
}