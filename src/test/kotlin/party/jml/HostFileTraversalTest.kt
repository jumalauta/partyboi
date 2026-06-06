package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HostFileTraversalTest : PartyboiTester {

    // Regression: /admin/host/{fileId}/{path...} resolved the {path...} wildcard against the extracted
    // entry directory without normalising it or checking containment, so "../../../../etc/passwd"
    // escaped the directory and served arbitrary files. The path must be confined to the directory.
    @Test
    fun testHostFileRejectsPathTraversal() = test {
        var fileId: UUID = UUIDv7.Empty

        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                val user = addTestUser(app, "submitter").bind()
                val compo = compos.add(NewCompo("Demo", "")).bind()
                val entry = entries.add(
                    NewEntry(
                        title = "Hosted",
                        author = "A",
                        file = FileUpload.createTestData("hosted.dat", 256),
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                fileId = app.files.getLatest(entry.id, true).bind().id
            }
        }

        it.login("admin")

        // Hosting the entry's own extracted directory works.
        assertEquals(
            HttpStatusCode.OK,
            it.client.get("/admin/host/$fileId").status,
            "an admin should be able to host the entry's own files",
        )

        // "../../../../../../../etc/passwd" (url-encoded) must not escape the directory.
        val traversal = "%2e%2e%2f".repeat(8) + "etc%2fpasswd"
        val resp = it.client.get("/admin/host/$fileId/$traversal")
        val body = resp.bodyAsText()
        assertEquals(
            HttpStatusCode.NotFound,
            resp.status,
            "path traversal must be rejected (status ${resp.status})",
        )
        assertFalse(body.contains("root:"), "must not serve the contents of /etc/passwd")
    }
}
