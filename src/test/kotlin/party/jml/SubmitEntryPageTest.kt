package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import it.skrape.matchers.toBeNot
import it.skrape.selects.text
import party.jml.partyboi.auth.UserCredentials
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.toDecimals
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.EntryUpdate
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.entries.NewScreenshot
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

    @Test
    fun testEditEntry() = test {
        var entry: Entry? = null

        services {
            either {
                entries.deleteAll().bind()
                compos.deleteAll().bind()
                val wildCompo = compos.add(NewCompo("Wild", "")).bind()
                compos.setVisible(wildCompo.id, true).bind()

                users.addUser(UserCredentials("user", "password", "password"), "0.0.0.0")
                val user = users.getUser("user").bind()

                entry = entries.add(
                    NewEntry(
                        title = "Turbo Färjan Race 2000",
                        author = "Jumalauta",
                        file = FileUpload.createTestData("jml-tfr2000.dat", 4096),
                        compoId = wildCompo.id,
                        screenComment = "Hello to audience",
                        orgComment = "Hello to orgs",
                        userId = user.id,
                    )
                ).bind()
            }
        }

        it.login()

        // Entry page shows correct data
        it.get("/entries/${entry!!.id}") {
            findFirst("article fieldset") {
                findFirst("select[name='compoId'] option[selected]") { text.toBe("Wild") }
                findFirst("input[name='title']") { attribute("value").toBe("Turbo Färjan Race 2000") }
                findFirst("input[name='author']") { attribute("value").toBe("Jumalauta") }
                findFirst("textarea[name='screenComment']") { text.toBe("Hello to audience") }
                findFirst("textarea[name='orgComment']") { text.toBe("Hello to orgs") }
            }

            findFirst(".fileversions") {
                findFirst("td") { text.toBe("1") }
                findSecond("td") { text.toBe("jml-tfr2000.dat") }
                findThird("td") { text.toBe("${4.1.toDecimals(1)} kB") }
            }
        }

        // Downloading and hosting the file works
        it.get("/entries/${entry!!.id}/download/1") {}

        // Update entry
        val updated = EntryUpdate(
            id = entry!!.id,
            title = "Mega Turbo Färjan 2000 Deluxe Plus Nitro",
            author = "Jumalauta Game Committee",
            file = FileUpload.createTestData("jml-mtf2kdpn.dat", 6000),
            compoId = entry!!.compoId,
            userId = entry!!.userId,
            screenComment = "Turbo!!!",
            orgComment = "Yeah!!!"
        )
        it.post("/entries/${entry!!.id}", updated) {
            it.redirectsTo("/entries")
        }

        // Check that fields contain the new data
        it.get("/entries/${entry!!.id}") {
            findFirst("article fieldset") {
                findFirst("select[name='compoId'] option[selected]") { text.toBe("Wild") }
                findFirst("input[name='title']") { attribute("value").toBe(updated.title) }
                findFirst("input[name='author']") { attribute("value").toBe(updated.author) }
                findFirst("textarea[name='screenComment']") { text.toBe(updated.screenComment) }
                findFirst("textarea[name='orgComment']") { text.toBe(updated.orgComment) }
            }

            findFirst(".fileversions") {
                findSecond("tr") {
                    findFirst("td") { text.toBe("2") }
                    findSecond("td") { text.toBe("jml-mtf2kdpn.dat") }
                    findThird("td") { text.toBe("${6.0.toDecimals(1)} kB") }
                }
                findThird("tr") {
                    findFirst("td") { text.toBe("1") }
                    findSecond("td") { text.toBe("jml-tfr2000.dat") }
                    findThird("td") { text.toBe("${4.1.toDecimals(1)} kB") }
                }
            }
        }
    }

    @Test
    fun testScreenshots() = test {
        var entry: Entry? = null

        services {
            either {
                entries.deleteAll().bind()
                compos.deleteAll().bind()
                val gfxCompo = compos.add(NewCompo("Graphics", "")).bind()
                compos.setVisible(gfxCompo.id, true).bind()

                users.addUser(UserCredentials("user", "password", "password"), "0.0.0.0")
                val user = users.getUser("user").bind()

                entry = entries.add(
                    NewEntry(
                        title = "Final Dog",
                        author = "Naettiii",
                        file = FileUpload.fromResource(this, "/images/image.zip")!!,
                        compoId = gfxCompo.id,
                        screenComment = "Hello to audience",
                        orgComment = "Hello to orgs",
                        userId = user.id,
                    )
                ).bind()
            }
        }

        it.login()

        // Test that the screenshot is shown on the entry page
        val screenshotUrl = "/entries/${entry!!.id}/screenshot.jpg"
        it.get("/entries/${entry!!.id}") {
            findFirst("figure img") { attribute("src").to(screenshotUrl) }
        }

        // Screenshot can be downloaded
        val originalScreenshotHash = "599cf1dbe77dfb63ea5526311d89c38c" // hash of resized final.png inside image.zip
        it.getBinary(screenshotUrl) {
            TestUtils.md5(it).toBe(originalScreenshotHash)
        }

        // Screenshot can be changed
        it.post(
            "/entries/${entry!!.id}/screenshot",
            NewScreenshot(FileUpload.fromResource(this, "/images/final2.png")!!)
        ) {}

        it.getBinary(screenshotUrl) {
            TestUtils.md5(it).toBeNot(originalScreenshotHash)
        }
    }
}