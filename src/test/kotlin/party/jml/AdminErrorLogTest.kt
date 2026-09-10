package party.jml

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.plugins.*
import it.skrape.matchers.toBe
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    // Vulnerability scanners probe static content with ../ paths; Ktor rejects them with a
    // BadRequestException before touching the filesystem. These must not clutter the error log.
    @Test
    fun testPathTraversalProbesAreNotLogged() = test {
        var appRef: party.jml.partyboi.AppServices? = null
        setupServices {
            appRef = this
            either { addTestAdmin(this@setupServices).bind() }
        }
        it.login("admin")

        val probe = BadRequestException(
            "Relative path should not contain path traversing characters: @fs/../../../../../root/.env"
        )
        assertNull(appRef!!.errors.saveSafely<String>(probe), "traversal probes must not be saved to the error log")

        val realError = RuntimeException("Something actually broke")
        assertNotNull(appRef!!.errors.saveSafely<String>(realError), "real errors must still be saved")
    }
}