package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test

class AdminUsersTest : PartyboiTester {
    @Test
    fun testAdminUsersPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/users", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAdminUsersPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/users", HttpStatusCode.OK) {
            relaxed = true
            findFirst("article header") { text.toBe("Users") }
        }
    }
}