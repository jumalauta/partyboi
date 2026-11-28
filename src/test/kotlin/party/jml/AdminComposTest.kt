package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.compos.NewManualResult
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
    fun testEditCompoPageRendersTabbedRedesign() = test {
        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Combined Demo", "")).bind()
                val entry = entries.add(
                    NewEntry(
                        title = "Neon Cathedral",
                        author = "Fairlight",
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

        val editHref = it.get("/admin/compos", HttpStatusCode.OK) {
            relaxed = true
            findFirst("td a") { attribute("href") }
        }

        it.get(editHref, HttpStatusCode.OK) {
            relaxed = true
            findFirst(".compo-edit h1") { text.toBe("Combined Demo compo") }
            assertEquals(2, findAll(".tab-bar .tab") { size })
            findFirst("tr[data-search] a") { text.toBe("Neon Cathedral") }
            findFirst(".pb-stat strong") { text.toBe("1") }
        }
    }

    @Test
    fun testEditManualResultUsesTabbedShell() = test {
        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val compo = compos.add(NewCompo("Best Photo", "")).bind()
                compos.update(compo.copy(manualResults = true)).bind()
                manualResults.add(
                    NewManualResult(
                        title = "",
                        author = "Alice",
                        scoreText = "42 pts",
                        screenComment = "",
                        compoId = compo.id,
                    )
                ).bind()
            }
        }

        it.login("admin")

        val compoHref = it.get("/admin/compos", HttpStatusCode.OK) {
            relaxed = true
            findFirst("td a") { attribute("href") }
        }
        val editHref = it.get(compoHref, HttpStatusCode.OK) {
            relaxed = true
            findFirst("a[href*='manual-results']") { attribute("href") }
        }

        it.get(editHref, HttpStatusCode.OK) {
            relaxed = true
            // Renders inside the Settings | Results tab shell with the result pre-filled.
            assertEquals(2, findAll(".tab-bar .tab") { size })
            findFirst("input[name=author]") { attribute("value").toBe("Alice") }
        }
    }

    @Test
    fun testCompoDownloadReturnsZip() = test {
        var compoId: java.util.UUID? = null
        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Tracked Music", "")).bind()
                compoId = compo.id
                val entry = entries.add(
                    NewEntry(
                        title = "Song",
                        author = "Musician",
                        file = FileUpload.createTestData("song.dat", 256),
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

        val bytes = it.client.get("/admin/compos/$compoId/download").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers["Content-Disposition"])
            response.readRawBytes()
        }
        val names = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(1, names.size, "zip should contain the single entry file, got: $names")
    }

    @Test
    fun testCompoDownloadSkipsQualifiedEntryWithoutFile() = test {
        var compoId: java.util.UUID? = null
        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Tracked Music", "")).bind()
                compoId = compo.id
                // A non-deliverable entry (e.g. a live show) runs first and has no file
                val liveShow = entries.add(
                    NewEntry(
                        title = "Live Show",
                        author = "Performer",
                        file = FileUpload.Empty,
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                entries.setQualified(liveShow.id, true).bind()
                val withFile = entries.add(
                    NewEntry(
                        title = "Song",
                        author = "Musician",
                        file = FileUpload.createTestData("song.dat", 256),
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                entries.setQualified(withFile.id, true).bind()
            }
        }

        it.login("admin")

        val bytes = it.client.get("/admin/compos/$compoId/download").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.readRawBytes()
        }
        val names = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(1, names.size, "only the entry with a file should be in the zip, got: $names")
        // The skipped entry keeps its slot, so the file is still numbered by its run order
        assertTrue(
            names.single().substringAfterLast('/').startsWith("02-"),
            "file should keep run order number 02, got: ${names.single()}"
        )
    }

    @Test
    fun testCompoDownloadIncludesOriginalNextToProcessedFile() = test {
        var compoId: java.util.UUID? = null
        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Tracked Music", "")).bind()
                compoId = compo.id
                // Originals are 256 bytes and processed versions 64 bytes, to tell them apart in the zip.
                // The .dat originals keep the upload post-processing from starting real conversions.
                listOf(
                    "Song" to "song (normalized).flac", // e.g. a tracker module rendered to FLAC
                    "Tune" to "tune (normalized).dat",  // same extension as the original
                ).forEach { (title, processedName) ->
                    val entry = entries.add(
                        NewEntry(
                            title = title,
                            author = "Musician",
                            file = FileUpload.createTestData("${title.lowercase()}.dat", 256),
                            compoId = compo.id,
                            screenComment = "",
                            orgComment = "",
                            userId = user.id,
                        )
                    ).bind()
                    entries.setQualified(entry.id, true).bind()
                    val processed = files.store(
                        tempFile = FileUpload.createTestData(processedName, 64).tempFile,
                        originalFilename = processedName,
                        processed = true,
                    ).bind()
                    entries.associateFile(processed.id, entry.id).bind()
                }
            }
        }

        it.login("admin")

        val bytes = it.client.get("/admin/compos/$compoId/download").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.readRawBytes()
        }
        val sizes = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes().size }
        }
        assertEquals(
            mapOf(
                "trackedmusic/01-musician-song.flac" to 64,
                "trackedmusic/01-musician-song.dat" to 256,
                "trackedmusic/02-musician-tune.dat" to 64,
                "trackedmusic/02-musician-tune-original.dat" to 256,
            ),
            sizes,
        )
    }

    @Test
    fun testToggleApiReportsErrors() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")

        // The switch helpers used to discard the result and answer "OK" even when the update failed
        val response = it.client.put("/admin/compos/${java.util.UUID.randomUUID()}/setVisible/true")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Not found", response.bodyAsText())
    }

    @Test
    fun testRunOrderApiReportsErrors() = test {
        var compoId: java.util.UUID? = null
        setupServices {
            either {
                addTestAdmin(this@setupServices).bind()
                compoId = compos.add(NewCompo("Demo", "")).bind().id
            }
        }
        it.login("admin")

        val response = it.client.post("/admin/compos/$compoId/runOrder") {
            contentType(ContentType.Application.Json)
            setBody("[\"${java.util.UUID.randomUUID()}\"]")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Not found", response.bodyAsText())
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

    @Test
    fun testDistributionZipHandlesCollidingEntryNames() = test {
        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Wild", "")).bind()
                // These titles sanitize to the same scene.org token
                listOf("Same Name!", "Same. Name?").forEach { title ->
                    val entry = entries.add(
                        NewEntry(
                            title = title,
                            author = "Author",
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
        }

        it.login("admin")

        val bytes = it.client.get("/admin/compos/entries.zip").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.readRawBytes()
        }

        val entryFiles = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val parts = entry.name.trimEnd('/').split('/')
                if (!entry.isDirectory && parts.size == 3 && parts[1] == "wild") {
                    entryFiles.add(parts[2])
                }
            }
        }

        assertEquals(
            listOf("author_same_name.dat", "author_same_name_2.dat"),
            entryFiles.sorted(),
            "both colliding entries should be in the package with unique names"
        )
    }
}