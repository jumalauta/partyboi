package party.jml

import arrow.core.raise.either
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryLifecycleTest : PartyboiTester {

    // Regression for the "preview silently lost on entry creation" bug: the preview used to be
    // written on a fresh connection inside the entry transaction (so it could not see the
    // uncommitted entry row) and its result was discarded, leaving image entries with no preview.
    @Test
    fun testPreviewIsGeneratedForImageEntry() = test {
        var previewExists: Boolean? = null

        setupServices {
            val app = this
            either {
                val user = addTestUser(app, "gfxuser").bind()
                val compo = compos.add(NewCompo("Graphics", "")).bind()
                val entry = entries.add(
                    NewEntry(
                        title = "Final Dog",
                        author = "Naettiii",
                        file = FileUpload.fromResource(app, "/images/image.zip")!!,
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                previewExists = app.previews.get(entry.id).isRight()
            }
        }

        it.login("gfxuser")

        assertEquals(true, previewExists, "An image entry should have a generated preview after creation")
    }

    // Regression for the broken admin "delete entry" path: it used invalid "DELETE ... CASCADE" SQL
    // (a syntax error in PostgreSQL), and even valid SQL would have been blocked by the preview
    // foreign key that lacked an ON DELETE rule. Deleting an entry that has a preview must succeed.
    @Test
    fun testAdminCanDeleteEntryWithPreview() = test {
        var appRef: AppServices? = null
        var entryId: UUID = UUIDv7.Empty

        setupServices {
            val app = this
            appRef = app
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Graphics", "")).bind()
                val entry = entries.add(
                    NewEntry(
                        title = "Doomed Entry",
                        author = "Author",
                        file = FileUpload.fromResource(app, "/images/image.zip")!!,
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                entryId = entry.id
                // The entry must actually have a preview, otherwise this would not exercise the
                // cascade-delete of the preview row.
                app.previews.get(entry.id).bind()
            }
        }

        it.login("admin")

        it.client.delete("/admin/compos/entries/$entryId").apply {
            assertEquals(HttpStatusCode.OK, status, "admin delete should succeed, got ${status}: ${bodyAsText()}")
        }

        assertTrue(appRef!!.entries.getById(entryId).isLeft(), "entry should be deleted")
        assertTrue(appRef!!.previews.get(entryId).isLeft(), "preview should be cascade-deleted with the entry")
    }

    // Regression for the broken owner check in /entries/download/{fileId}: it compared the session
    // user id with the entry's user id using === (reference equality), which is always false for two
    // distinct UUID instances, so authors could not download their own entry before results were
    // public. A non-owner must still be denied while results are not public.
    //
    // We assert on the Content-Disposition header (the file is served as an attachment) rather than
    // the status code, because the denial path renders an HTML page that this SSR app returns with a
    // 200 status — so the status alone cannot tell "file served" apart from "access denied page".
    @Test
    fun testOwnerCanDownloadOwnFileBeforeResultsArePublic() = test {
        var ownerFileId: UUID = UUIDv7.Empty

        setupServices {
            val app = this
            either {
                val owner = addTestUser(app, "owner").bind()
                val compo = compos.add(NewCompo("Demo", "")).bind() // results not public (default)
                val entry = entries.add(
                    NewEntry(
                        title = "Owned Entry",
                        author = "Owner",
                        file = FileUpload.createTestData("owned.dat", 256),
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = owner.id,
                    )
                ).bind()
                ownerFileId = app.files.getLatest(entry.id, true).bind().id
            }
        }

        // The author receives their own file as an attachment, even though results are not public.
        it.login("owner")
        assertEquals(
            "attachment; filename=owned.dat",
            it.client.get("/entries/download/$ownerFileId").headers[HttpHeaders.ContentDisposition],
            "owner should receive their own file before results are public",
        )

        // A different (non-admin) user is still denied: the response is the access-denied page, not
        // the file (no attachment Content-Disposition).
        val other = TestHtmlClient(createClient { install(HttpCookies) })
        other.login("otheruser")
        assertNull(
            other.client.get("/entries/download/$ownerFileId").headers[HttpHeaders.ContentDisposition],
            "a non-owner must not receive the file while results are not public",
        )
    }
}
