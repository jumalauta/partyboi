package party.jml

import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import it.skrape.selects.html5.li
import it.skrape.selects.text
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
        setupServices()

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
            it.redirectsTo("/entries")
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
            findFirst(".error") { text.toBe("The user name has already been registered") }
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
}

