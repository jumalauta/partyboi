package party.jml

import arrow.core.raise.either
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
}
