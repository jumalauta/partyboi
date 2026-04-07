package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test

class AdminErrorLogTest : PartyboiTester {
    @Test
    fun testErrorLogPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/errors", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testErrorLogPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/errors", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Error log") }
        }
    }
}