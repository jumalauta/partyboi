package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import java.util.*
import io.ktor.client.statement.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PreviewUploadTest : PartyboiTester {
    private fun setupEntry(
        builder: io.ktor.server.testing.ApplicationTestBuilder,
        file: FileUpload = FileUpload.createTestData("demo.dat", 256),
        onReady: (AppServices, UUID) -> Unit,
    ) {
        builder.setupServices {
            val app = this
            either {
                val user = addTestUser(app, "pnguser").bind()
                addTestAdmin(app).bind()
                val compo = compos.add(NewCompo("Demo", "")).bind()
                compos.setVisible(compo.id, true).bind()
                val entry = entries.add(
                    NewEntry(
                        title = "Png Entry",
                        author = "Author",
                        file = file,
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                onReady(app, entry.id)
            }
        }
    }

    @Test
    fun testUploadPngPreview() = test {
        var appRef: AppServices? = null
        var entryId: UUID = UUIDv7.Empty
        setupEntry(this) { app, id ->
            appRef = app
            entryId = id
        }

        it.login("pnguser")

        val pngBytes = javaClass.getResource("/images/final.png")!!.readBytes()
        it.post("/entries/$entryId/preview", formData {
            append("file", pngBytes, headers {
                append("Content-Type", "image/png")
                append("Content-Disposition", "form-data; name=\"file\"; filename=\"final.png\"")
            })
        }) {
            it.redirectsTo("/entries/$entryId")
        }

        val preview = appRef!!.previews.get(entryId)
        assertTrue(preview.isRight(), "preview should exist after PNG upload, got $preview")
    }

    // Regression for the silently swallowed preview upload: PreviewRepository.store discarded the
    // conversion result and the route dropped the AppResult, so a file that could not be decoded
    // redirected as if the upload had succeeded. A broken image must render an error, not redirect.
    @Test
    fun testUploadBrokenImageShowsError() = test {
        var appRef: AppServices? = null
        var entryId: UUID = UUIDv7.Empty
        setupEntry(this) { app, id ->
            appRef = app
            entryId = id
        }

        it.login("pnguser")

        it.post("/entries/$entryId/preview", formData {
            append("file", "this is not an image".toByteArray(), headers {
                append("Content-Type", "image/png")
                append("Content-Disposition", "form-data; name=\"file\"; filename=\"broken.png\"")
            })
        }) { headers ->
            assertTrue(
                headers[HttpHeaders.Location] == null,
                "a broken image upload must not redirect as if it succeeded",
            )
        }

        assertTrue(
            appRef!!.previews.get(entryId).isLeft(),
            "no preview should be stored for a broken image",
        )
    }

    @Test
    fun testAdminRegeneratesPreviewFromImageEntryFile() = test {
        var appRef: AppServices? = null
        var entryId: UUID = UUIDv7.Empty
        val pngBytes = javaClass.getResource("/images/final.png")!!.readBytes()
        setupEntry(this, file = FileUpload.fromByteArray("final.png", pngBytes)) { app, id ->
            appRef = app
            entryId = id
        }

        it.login("admin", "password")

        // The image entry file auto-generated a preview at submit time.
        val originalThumbnail = appRef!!.previews.get(entryId)
            .fold({ error("preview should exist after submitting an image entry: $it") }, { it.systemPath })

        val response = it.client.post("/admin/entries/$entryId/regenerate-preview")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/entries/$entryId", response.headers[HttpHeaders.Location])

        val regeneratedThumbnail = appRef!!.previews.get(entryId)
            .fold({ error("preview should exist after regeneration: $it") }, { it.systemPath })
        assertNotEquals(
            originalThumbnail,
            regeneratedThumbnail,
            "regeneration must store a fresh thumbnail file",
        )
    }

    @Test
    fun testRegeneratePreviewRequiresAdmin() = test {
        var entryId: UUID = UUIDv7.Empty
        setupEntry(this) { _, id -> entryId = id }

        it.login("pnguser")

        val response = it.client.post("/admin/entries/$entryId/regenerate-preview")
        assertNotEquals("/entries/$entryId", response.headers[HttpHeaders.Location])
    }

    @Test
    fun testRegeneratePreviewFromUnsupportedFileType() = test {
        var entryId: UUID = UUIDv7.Empty
        setupEntry(this) { _, id -> entryId = id }

        it.login("admin", "password")

        // The default .dat entry file has no preview source: the entry page renders
        // again with the error shown instead of redirecting.
        val response = it.client.post("/admin/entries/$entryId/regenerate-preview")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.Location] == null, "a failed regeneration must not redirect")
        assertContains(response.bodyAsText(), "A preview cannot be generated from demo.dat")
    }
}
