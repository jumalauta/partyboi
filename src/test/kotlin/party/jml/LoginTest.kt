package party.jml

import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.core.htmlDocument
import it.skrape.matchers.toBe
import it.skrape.selects.html5.li
import it.skrape.selects.text
import party.jml.partyboi.email.EmailMessage
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.settings.VoteSettings
import kotlin.test.Test

class LoginTest : PartyboiTester {
    @Test
    fun testFrontPage() = test {
        it.get("/", HttpStatusCode.OK) {
            findFirst("main header") {
                li {
                    findFirst { text.toBe("Partyboi (test)") }
                    findSecond { text.toBe("Login") }
                    findThird { text.toBe("Register") }
                }
            }
            findAll(".page-nav li") { text.toBe("Info Compos Results") }
        }
    }

    @Test
    fun testRegistration() = test {
        val userName = "foobar"

        // User cannot see the entry page before registration
        it.get("/entries") {
            findFirst("article header") { text.toBe("Login") }
        }

        // Check that registration page loads
        it.get("/register") {}

        // Register with invalid data -> shows errors
        it.post(
            "/register",
            formData {
                append("name", "")
                append("password", "hunter2")
                append("password2", "password")
                append("isUpdate", "")
            }
        ) {
            findFirst(".error") { text.toBe("Value cannot be empty") }
            findSecond(".error") { text.toBe("Minimum length is 8 characters") }
            findThird(".error") { text.toBe("Value is not equal") }
        }

        // Register with good data -> redirects to entry page
        it.post(
            "/register",
            formData {
                append("name", userName)
                append("password", "password")
                append("password2", "password")
                append("isUpdate", "")
            }
        ) {
            it.redirectsTo("/")
        }

        // User can see the entry page now
        it.get("/entries") {
            findFirst("h1") { text.toBe("Entries") }
        }

        // Register with same username again -> shows an error
        it.post(
            "/register",
            formData {
                append("name", userName)
                append("password", "password")
                append("password2", "password")
                append("isUpdate", "")
            }
        ) {
            findFirst(".error") { text.toBe("The user name or email has already been registered") }
        }
    }

    @Test
    fun loginTest() = test {
        val username = "zorro"
        val password = "password"

        setupServices {
            addTestUser(this, username, password)
        }

        // Check that login page loads
        it.get("/login") {}

        // Login with invalid credentials fails and error is shown
        it.post("/login", formData {
            append("name", username)
            append("password", "xyzzyfoo")
        }) {
            findFirst(".error") { text.toBe("Invalid user name or password") }
        }

        // Login with good credentials works and redirects user to front page
        it.post("/login", formData {
            append("name", username)
            append("password", password)
        }) {
            it.redirectsTo("/")
        }
    }

    @Test
    fun `register with email which has been preregistered for automatic vote key`() = test {
        val userName = "zorro"
        val emailAddr = "zorro@email.com"

        setupServices {
            settings.saveSettings(
                VoteSettings(
                    automaticVoteKeys = AutomaticVoteKeys.PER_EMAIL,
                    listOfEmails = emailAddr,
                    verifiedEmailsOnly = true
                )
            )
        }

        // Register with email
        it.post(
            "/register",
            formData {
                append("name", userName)
                append("password", "password")
                append("password2", "password")
                append("email", emailAddr)
                append("isUpdate", "")
            }
        ) {
            it.redirectsTo("/")
        }

        // Expect that we cannot vote yet
        it.get("/") {
            findFirst("a[href='/vote/register']") {
                text.toBe("Register vote key")
            }
        }

        // Expect that we got mail
        val verificationLink = it.getJson<List<EmailMessage>, _>("/test/mock-emails") { emails ->
            emails.last().let {
                it.recipient.toBe(emailAddr)
                it.subject.toBe("Verify your email address to Partyboi (test)")
                // Scrape the verification link
                htmlDocument(it.content) {
                    findFirst("a") {
                        attribute("href").replace("localhost", "")
                    }
                }
            }
        }

        // Verificate email
        it.get(verificationLink) {
            findFirst(".snackbars li span") {
                text.toBe("Your email has been verified successfully.")
            }
            findSecond(".snackbars li span") {
                text.toBe("You have been granted rights to vote.")
            }
            findFirst("a[href='/vote']") {
                text.toBe("Voting")
            }
        }
    }
}

