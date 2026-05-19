package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test

class AdminAssetsTest : PartyboiTester {
    @Test
    fun testAssetsPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/assets", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAssetsPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/assets", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Assets") }
        }
    }

    @Test
    fun testUploadSingleFile() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.post("/admin/assets", formData {
            append("files", ByteArray(128), headers {
                append("Content-Type", "application/octet-stream")
                append("Content-Disposition", "form-data; name=\"files\"; filename=\"test.dat\"")
            })
        }) {
            it.redirectsTo("/admin/assets")
        }
        it.get("/admin/assets", HttpStatusCode.OK) {
            relaxed = true
            findFirst("tbody td a") { attribute("href").toBe("/assets/test.dat") }
        }
    }

    @Test
    fun testUploadMultipleFiles() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.post("/admin/assets", formData {
            append("files", ByteArray(64), headers {
                append("Content-Type", "application/octet-stream")
                append("Content-Disposition", "form-data; name=\"files\"; filename=\"alpha.dat\"")
            })
            append("files", ByteArray(64), headers {
                append("Content-Type", "application/octet-stream")
                append("Content-Disposition", "form-data; name=\"files\"; filename=\"beta.dat\"")
            })
        }) {
            it.redirectsTo("/admin/assets")
        }
        it.get("/admin/assets", HttpStatusCode.OK) {
            relaxed = true
            findAll("td a") {
                val names = map { it.text }
                assert("alpha.dat" in names) { "Expected alpha.dat in $names" }
                assert("beta.dat" in names) { "Expected beta.dat in $names" }
            }
        }
    }

    @Test
    fun testUploadNoFilesShowsError() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.post("/admin/assets", formData {
            append("files", "", headers {
                append("Content-Type", "application/octet-stream")
                append("Content-Disposition", "form-data; name=\"files\"; filename=\"\"")
            })
        }) {
            findFirst(".error") { text.toBe("No files selected") }
        }
    }
}