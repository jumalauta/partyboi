package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import it.skrape.selects.text
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import kotlin.test.Test

class SubmitEntryPageTest : PartyboiTester {
    @Test
    fun testSubmitEntry() = test {
        var compoId: Int = -1

        services {
            either {
                entries.deleteAll().bind()
                compos.deleteAll().bind()
                val secretCompo = compos.add(NewCompo("Secret", "")).bind()
                val demoCompo = compos.add(NewCompo("Demo", "")).bind()
                val musicCompo = compos.add(NewCompo("Music", "")).bind()

                compos.setVisible(demoCompo.id, true).bind()
                compos.setVisible(musicCompo.id, true).bind()

                compoId = demoCompo.id
            }
        }

        it.login()

        // Check that compo selector has correct options
        it.get("/entries") {
            findFirst("article select") {
                findAll("option").text.toBe("Demo Music")
            }

        }

        // Submit bad form
        it.post("/entries", formData {
            append("file", "", headers {
                append("Content-Type", "application/octet-stream")
                append("Content-Disposition", "form-data; name=\"file\"; filename=\"\"")
            })
        }) {
            findFirst(".error") { text.toBe("Value cannot be empty") }
            findSecond(".error") { text.toBe("Value cannot be empty") }
        }

        // Submit a good entry
        val goodEntry = NewEntry(
            title = "Per Celer",
            author = "Matt Current",
            file = FileUpload.createTestData("mc-perceler.dat", 4096),
            compoId = compoId,
            screenComment = "Hello to audience",
            orgComment = "Hello to orgs",
            userId = -1
        )
        it.post("/entries", goodEntry) {
            it.redirectsTo("/entries")
        }

        // Check that the uploaded entry is shown on the entries page
        it.get("/entries") {
            findFirst("article.entry") {
                findFirst("th") { text.toBe("Title") }
                findFirst("td") { text.toBe("Per Celer") }
                findSecond("th") { text.toBe("Author") }
                findSecond("td") { text.toBe("Matt Current") }
                findThird("th") { text.toBe("Compo") }
                findThird("td") { text.toBe("Demo") }
            }
        }
    }
}