package party.jml.partyboi.email.brevo

import arrow.core.left
import arrow.core.right
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.email.EmailMessage
import party.jml.partyboi.email.EmailService
import party.jml.partyboi.system.AppResult

class BrevoEmailService(val app: AppServices, val apiKey: String) : EmailService {
    private val client: HttpClient get() = HttpClient(CIO)

    override suspend fun sendMail(message: EmailMessage): AppResult<Unit> {
        val response = client.use { client ->
            client.request("https://api.brevo.com/v3/smtp/email") {
                method = HttpMethod.Post
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                    append("api-key", apiKey)
                }
                setBody(
                    Json.encodeToString(
                        BrevoEmail(
                            sender = BrevoEmail.Contact("donotreply@partyboi.app", "Partyboi"),
                            to = listOf(BrevoEmail.Contact(message.recipient)),
                            subject = message.subject,
                            htmlContent = message.content,
                        )
                    )
                )
            }
        }

        return when (response.status) {
            HttpStatusCode.OK -> Unit.right()
            HttpStatusCode.Created -> Unit.right()
            HttpStatusCode.Accepted -> Unit.right()
            HttpStatusCode.BadRequest -> BadRequestBrevoError(response.body<BadRequestBrevoError.Body>()).left()
            else -> UnexpectedBrevoError(response.body<String>()).left()
        }
    }
}

@Serializable
data class BrevoEmail(
    val sender: Contact,
    val to: List<Contact>,
    val subject: String,
    val htmlContent: String,
) {
    @Serializable
    data class Contact(
        val email: String,
        val name: String? = null
    )
}

class BadRequestBrevoError(val body: Body) : AppError {
    override val message: String = body.message
    override val statusCode: HttpStatusCode = HttpStatusCode.InternalServerError
    override val throwable: Throwable? = Error(message)

    data class Body(
        val code: String,
        val message: String
    )
}

class UnexpectedBrevoError(override val message: String) : AppError {
    override val statusCode: HttpStatusCode = HttpStatusCode.InternalServerError
    override val throwable: Throwable? = Error(message)
}
