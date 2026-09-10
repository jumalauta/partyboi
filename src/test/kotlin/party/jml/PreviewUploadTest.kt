package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue

class PreviewUploadTest : PartyboiTester {
    private fun setupEntry(
        builder: io.ktor.server.testing.ApplicationTestBuilder,
        onReady: (AppServices, UUID) -> Unit,
    ) {
        builder.setupServices {
            val app = this
            either {
                val user = addTestUser(app, "pnguser").bind()
                val compo = compos.add(NewCompo("Demo", "")).bind()
                compos.setVisible(compo.id, true).bind()
                val entry = entries.add(
                    NewEntry(
                        title = "Png Entry",
                        author = "Author",
                        file = FileUpload.createTestData("demo.dat", 256),
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
}
