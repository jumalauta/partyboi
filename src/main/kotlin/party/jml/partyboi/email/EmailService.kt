package party.jml.partyboi.email

import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import kotlinx.html.BODY
import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.stream.createHTML
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

    override suspend fun sendMail(message: EmailMessage): AppResult<Unit> =
        instance.flatMap { it.sendMail(message) }
}

interface EmailService {
    suspend fun sendMail(message: EmailMessage): AppResult<Unit>
}

data class EmailMessage(
    val recipient: String,
    val subject: String,
    val content: String,
) {
    companion object {
        fun build(recipient: String, subject: String, content: BODY.() -> Unit): EmailMessage =
            EmailMessage(recipient, subject, createHTML().html { body { content() } })
    }
}
