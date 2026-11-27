package party.jml

import arrow.core.right
import io.ktor.client.request.forms.*
import it.skrape.core.htmlDocument
import it.skrape.matchers.toBe
import it.skrape.selects.text
import party.jml.partyboi.email.EmailMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordResetTest : PartyboiTester {
    private val userName = "zorro"
    private val emailAddr = "zorro@email.com"

    private suspend fun TestHtmlClient.register() {
        post("/register", formData {
            append("name", userName)
            append("password", "password")
            append("password2", "password")
            append("email", emailAddr)
            append("isUpdate", "")
        }) { it.redirectsTo("/") }
        get("/logout") {}
    }

    private suspend fun TestHtmlClient.mockEmails(): List<EmailMessage> =
        getJson<List<EmailMessage>, _>("/test/mock-emails") { it }

    @Test
    fun `forgotten password can be reset via emailed link`() = test {
        setupServices { Unit.right() }
        it.register()

        it.post("/reset-password", formData { append("email", emailAddr) }) {
            it.redirectsTo("/reset-password-sent")
        }

        // The recovery mail is sent and carries a reset link
        val resetMail = it.mockEmails().last()
        resetMail.recipient.toBe(emailAddr)
        resetMail.subject.toBe("Reset your Partyboi (test) password")
        val resetLink = htmlDocument(resetMail.content) {
            findFirst("a") { attribute("href").replace("localhost", "") }
        }

        // The link opens the new password form, which changes the password and logs the user in
        val code = it.get(resetLink) {
            findFirst("input[name='code']") { attribute("value") }
        }
        it.post("/reset-password/change", formData {
            append("code", code)
            append("password", "newpassword")
            append("password2", "newpassword")
        }) { it.redirectsTo("/") }

        // The new password works, the old one does not, and the code cannot be reused
        it.get("/logout") {}
        it.post("/login", formData {
            append("name", userName)
            append("password", "password")
        }) {
            findFirst(".error") { text.toBe("Invalid user name or password") }
        }
        it.post("/login", formData {
            append("name", userName)
            append("password", "newpassword")
        }) { it.redirectsTo("/") }
        it.get(resetLink) {
            findFirst("article.error") {
                text.toBe("The password reset code is invalid or expired. Try again.")
            }
        }
    }

    @Test
    fun `unknown email gets the same response but no mail`() = test {
        setupServices { Unit.right() }
        it.register()
        val mailsBefore = it.mockEmails().size

        it.post("/reset-password", formData { append("email", "nobody@email.com") }) {
            it.redirectsTo("/reset-password-sent")
        }

        assertEquals(mailsBefore, it.mockEmails().size)
    }
}
