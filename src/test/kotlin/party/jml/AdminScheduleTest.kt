package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test

class AdminScheduleTest : PartyboiTester {
    @Test
    fun testAdminSchedulePageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/schedule", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAdminSchedulePageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/schedule", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Schedule") }
        }
    }
}