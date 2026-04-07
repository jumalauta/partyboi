package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test

class AdminScreenTest : PartyboiTester {
    @Test
    fun testAdminScreenPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/screen/default", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAdminScreenPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/screen/default", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Default") }
        }
    }
}