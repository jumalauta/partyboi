package party.jml

import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.DEFAULT_MAX_UPLOAD_SIZE
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.multipartSizeLimit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadSizeTest : PartyboiTester {

    // Regression: app.config.maxFileUploadSize was passed to receiveMultipart as formFieldLimit and
    // defaulted to -1 (unlimited) when files.maxSize was unset, so uploads were unbounded. A file
    // larger than the configured limit (5 MB in tests.yaml) must be rejected instead of stored.
    @Test
    fun testOversizedFileIsRejected() = test {
        var appRef: AppServices? = null
        var compoId = UUIDv7.Empty

        setupServices {
            appRef = this
            either {
                val demo = compos.add(NewCompo("Demo", "")).bind()
                compos.setVisible(demo.id, true).bind()
                compoId = demo.id
            }
        }

        it.login()

        // 2 MB: over the 1 MB text-form default but under the 5 MB file limit -> accepted (this also
        // proves the entry upload route uses the large file limit, not the small form-field default).
        it.post("/entries", entryWithFile("Accepted", 2_000_000, compoId)) {}

        // Over the limit -> rejected. The server stops reading the oversized body, which can surface
        // as a client-side connection error; either way no entry must be created.
        try {
            it.post("/entries", entryWithFile("Rejected", 6_000_000, compoId)) {}
        } catch (_: Exception) {
        }

        assertEquals(
            listOf("Accepted"),
            compoEntryTitles(appRef!!, compoId),
            "the oversized file upload must be rejected; only the under-limit entry should exist",
        )
    }

    // The limit handed to Ktor's receiveMultipart must never be unlimited (-1), which is what an
    // unconfigured files.maxSize used to produce. Text-only forms get this via the small default
    // (see processForm), file forms via app.config.maxFileUploadSize — both go through this helper.
    @Test
    fun testMultipartLimitIsNeverUnlimited() {
        assertEquals(DEFAULT_MAX_UPLOAD_SIZE, multipartSizeLimit(-1), "unset limit must fall back to a finite default")
        assertEquals(DEFAULT_MAX_UPLOAD_SIZE, multipartSizeLimit(0), "zero limit must fall back to a finite default")
        assertEquals(1_234_567L, multipartSizeLimit(1_234_567L), "a configured limit is used as-is")
    }

    private suspend fun compoEntryTitles(app: AppServices, compoId: UUID): List<String> =
        app.entries.getEntriesForCompo(compoId).getOrNull().orEmpty().map { it.title }

    private fun entryWithFile(title: String, fileBytes: Int, compoId: UUID) = NewEntry(
        title = title,
        author = "A",
        file = FileUpload.createTestData("$title.dat", fileBytes),
        compoId = compoId,
        screenComment = "",
        orgComment = "",
        userId = UUIDv7.Empty,
    )
}
