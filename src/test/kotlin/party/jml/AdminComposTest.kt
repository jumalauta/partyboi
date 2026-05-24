package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminComposTest : PartyboiTester {
    @Test
    fun testAdminComposPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/compos", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAdminComposPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/compos", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Compos") }
        }
    }

    @Test
    fun testDistributionZipFollowsSceneOrgRules() = test {
        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Demo Compo!", "")).bind()
                val entry = entries.add(
                    NewEntry(
                        title = "Möö Demo (Final)",
                        author = "Jöhn-Döe (TPB)!",
                        file = FileUpload.createTestData("demo.dat", 256),
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                entries.setQualified(entry.id, true).bind()
            }
        }

        it.login("admin")

        val bytes = it.client.get("/admin/compos/entries.zip").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val disposition = response.headers["Content-Disposition"]
            assertNotNull(disposition)
            val match = Regex("filename=\"?([^\";]+)\"?").find(disposition)
            assertNotNull(match)
            val filename = match.groupValues[1]
            assertTrue(
                Regex("^[a-z0-9_]+\\.zip$").matches(filename),
                "zip filename '$filename' does not match scene.org pattern"
            )
            response.readRawBytes()
        }

        val tokenRegex = Regex("^[a-z0-9_]+$")
        val fileRegex = Regex("^[a-z0-9_]+\\.[a-z0-9]+$")
        val topLevelDirRegex = Regex("^[a-z0-9_]+\\d{4}$")

        val rootDirs = mutableSetOf<String>()
        val topLevelFiles = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val parts = entry.name.trimEnd('/').split('/')
                assertTrue(parts.isNotEmpty(), "empty zip entry name")
                rootDirs.add(parts.first())
                parts.forEachIndexed { i, segment ->
                    if (i == parts.lastIndex && !entry.isDirectory) {
                        assertTrue(
                            fileRegex.matches(segment),
                            "filename '$segment' in '${entry.name}' violates scene.org rule"
                        )
                    } else {
                        assertTrue(
                            tokenRegex.matches(segment),
                            "directory '$segment' in '${entry.name}' violates scene.org rule"
                        )
                    }
                }
                if (parts.size == 2 && !entry.isDirectory) {
                    topLevelFiles.add(parts[1])
                }
            }
        }

        assertEquals(1, rootDirs.size, "zip should have a single top-level dir, got: $rootDirs")
        val rootDir = rootDirs.single()
        assertTrue(
            topLevelDirRegex.matches(rootDir),
            "top-level dir '$rootDir' should match <partyname><year>"
        )
        assertTrue("results.txt" in topLevelFiles, "results.txt missing at top of package dir")
        assertTrue("upload.sh" in topLevelFiles, "upload.sh missing at top of package dir")
        assertTrue("upload.bat" in topLevelFiles, "upload.bat missing at top of package dir")
    }
}