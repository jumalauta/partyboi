package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test

class AdminSettingsTest : PartyboiTester {
    @Test
    fun testSettingsPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/settings", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testSettingsPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/settings", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Settings") }
        }
    }
}