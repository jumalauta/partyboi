package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import it.skrape.matchers.toBe
import it.skrape.selects.html5.h1
import kotlin.test.Test

class ProfilePageTest : PartyboiTester {
    @Test
    fun testProfilePageRequiresLogin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.get("/profile", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testProfilePageLoads() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/profile", HttpStatusCode.OK) {
            h1 { findFirst { text.toBe("User: user") } }
        }
    }
}