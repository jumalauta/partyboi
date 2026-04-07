package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test

class AdminVotingTest : PartyboiTester {
    @Test
    fun testAdminVotingPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/voting", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAdminVotingPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/voting", HttpStatusCode.OK) {
            relaxed = true
            findFirst("article header") { text.toBe("Settings") }
        }
    }

    @Test
    fun testGenerateVoteKeysPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/voting/generate", HttpStatusCode.OK) {
            relaxed = true
            findFirst("article header") { text.toBe("Generate vote keys") }
        }
    }
}