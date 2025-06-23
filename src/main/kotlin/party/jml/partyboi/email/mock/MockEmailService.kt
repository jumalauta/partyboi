package party.jml.partyboi.email.mock

import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.email.EmailMessage
import party.jml.partyboi.email.EmailService
import party.jml.partyboi.services
import party.jml.partyboi.system.AppResult

class MockEmailService : EmailService {
    val messages = mutableListOf<EmailMessage>()

    override fun configure(app: Application) {
        app.routing {
            get("/test/mock-emails") {
                val service = call.application.services().email.instance.getOrNull() as? MockEmailService
                call.respond(service!!.messages)
            }
        }
    }

    override fun reset() {
        messages.clear()
    }

    override suspend fun sendMail(message: EmailMessage): AppResult<Unit> {
        messages.add(message)
        return Unit.right()
    }
}