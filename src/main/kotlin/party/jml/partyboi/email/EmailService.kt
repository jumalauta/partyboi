package party.jml.partyboi.email

import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import io.ktor.server.application.*
import kotlinx.html.BODY
import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.email.brevo.BrevoEmailService
import party.jml.partyboi.email.mock.MockEmailService
import party.jml.partyboi.system.AppResult

class EmailServiceFacade(app: AppServices) : EmailService {
    val instance: AppResult<EmailService> by lazy {
        if (app.config.mockEmail == true) {
            MockEmailService().right()
        } else app.config.brevoApiKey?.let { apiKey ->
            BrevoEmailService(app, apiKey).right()
        } ?: InternalServerError(Error("Email service has not been configured")).left()
    }

    fun isConfigured(): Boolean = instance.isRight()

    override suspend fun sendMail(message: EmailMessage): AppResult<Unit> =
        instance.flatMap { it.sendMail(message) }

    override fun configure(app: Application) {
        instance.map { it.configure(app) }
    }

    override fun reset() {
        instance.map { it.reset() }
    }
}

interface EmailService {
    suspend fun sendMail(message: EmailMessage): AppResult<Unit>

    // For tests
    fun configure(app: Application) {}
    fun reset() {}
}

@Serializable
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
