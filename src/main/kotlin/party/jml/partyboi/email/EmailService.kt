package party.jml.partyboi.email

import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import kotlinx.html.HTML
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.email.brevo.BrevoEmailService
import party.jml.partyboi.system.AppResult

class EmailServiceFacade(app: AppServices) : EmailService {
    private val instance: AppResult<EmailService> by lazy {
        app.config.brevoApiKey?.let { apiKey ->
            BrevoEmailService(app, apiKey).right()
        } ?: InternalServerError(Error("Email service has not been configured")).left()
    }

    fun isConfigured(): Boolean = instance.isRight()

    override suspend fun sendMail(
        to: String,
        subject: String,
        content: HTML.() -> Unit
    ): AppResult<Unit> = instance.flatMap {
        it.sendMail(to, subject, content)
    }
}

interface EmailService {
    suspend fun sendMail(to: String, subject: String, content: HTML.() -> Unit): AppResult<Unit>
}
