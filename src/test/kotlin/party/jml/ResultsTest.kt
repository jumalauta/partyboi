package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import it.skrape.selects.html5.article
import it.skrape.selects.text
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.settings.AutomaticVoteKeys
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultsTest : PartyboiTester {
    @Test
    fun testResults() = test {
        setupServices {
            val app = this
            either {
                val demoCompo = compos.add(NewCompo("Demo", "")).bind()
                val musicCompo = compos.add(NewCompo("Music", "")).bind()
                val gfxCompo = compos.add(NewCompo("Graphics", "")).bind()

                compos.setVisible(demoCompo.id, true).bind()
                compos.setVisible(musicCompo.id, true).bind()

                settings.automaticVoteKeys.set(AutomaticVoteKeys.PER_USER)
                val users = (0..9).map { addTestUser(app, "user$it") }.bindAll()

                val demos = users.mapIndexed { index, user ->
                    entries.add(
                        NewEntry(
                            title = "Demo #$index",
                            author = "Author #$index",
                            file = FileUpload.createTestData("demo$index.dat", 256),
                            compoId = demoCompo.id,
                            screenComment = if (index == 0) "Runs on Amiga 500" else "",
                            orgComment = "",
                            userId = user.id,
                        )
                    )
                }.bindAll()

                val songs = users.mapIndexed { index, user ->
                    entries.add(
                        NewEntry(
                            title = "Track #$index",
                            author = "Author #$index",
                            file = FileUpload.createTestData("track$index.dat", 256),
                            compoId = musicCompo.id,
                            screenComment = "",
                            orgComment = "",
                            userId = user.id,
                        )
                    )
                }.bindAll()

                compos.allowSubmit(demoCompo.id, false).bind()
                compos.allowSubmit(musicCompo.id, false).bind()

                compos.allowVoting(demoCompo.id, true).bind()
                compos.allowVoting(musicCompo.id, true).bind()

                users.forEachIndexed { userIndex, user ->
                    demos.forEachIndexed { entryIndex, entry ->
                        votes.castVote(user, entry.id, simulatePoints(0, userIndex, entryIndex)).bind()
                    }
                    songs.forEachIndexed { entryIndex, entry ->
                        votes.castVote(user, entry.id, simulatePoints(13, userIndex, entryIndex)).bind()
                    }
                }

                compos.allowVoting(demoCompo.id, false).bind()
                compos.allowVoting(musicCompo.id, false).bind()

                compos.publishResults(demoCompo.id, true).bind()
                compos.publishResults(musicCompo.id, true).bind()
            }
        }

        it.get("/results") {
            article {
                findFirst {
                    findFirst("header") { text.toBe("Demo compo") }
                    equalResults(
                        findAll(".result-card").text,
                        "1. Author #5 – Demo #5 29 pts Download DAT · 256 B",
                        "2. Author #4 – Demo #4 26 pts Download DAT · 256 B",
                        "2. Author #9 – Demo #9 26 pts Download DAT · 256 B",
                        "4. Author #8 – Demo #8 24 pts Download DAT · 256 B",
                        "5. Author #6 – Demo #6 22 pts Download DAT · 256 B",
                        "6. Author #3 – Demo #3 19 pts Download DAT · 256 B",
                        "6. Author #7 – Demo #7 19 pts Download DAT · 256 B",
                        "8. Author #0 – Demo #0 14 pts Runs on Amiga 500 Download DAT · 256 B",
                        "8. Author #1 – Demo #1 14 pts Download DAT · 256 B",
                        "8. Author #2 – Demo #2 14 pts Download DAT · 256 B"
                    )
                    findFirst("a.download-link") {
                        assertTrue(attribute("href").startsWith("/entries/download/"))
                    }
                    findFirst(".entry-info") { text.toBe("Runs on Amiga 500") }
                }

                findSecond {
                    findFirst("header") { text.toBe("Music compo") }
                    equalResults(
                        findAll(".result-card").text,
                        "1. Author #6 – Track #6 25 pts Download DAT · 256 B",
                        "2. Author #4 – Track #4 24 pts Download DAT · 256 B",
                        "2. Author #9 – Track #9 24 pts Download DAT · 256 B",
                        "4. Author #2 – Track #2 23 pts Download DAT · 256 B",
                        "4. Author #3 – Track #3 23 pts Download DAT · 256 B",
                        "4. Author #7 – Track #7 23 pts Download DAT · 256 B",
                        "7. Author #8 – Track #8 22 pts Download DAT · 256 B",
                        "8. Author #5 – Track #5 18 pts Download DAT · 256 B",
                        "9. Author #0 – Track #0 14 pts Download DAT · 256 B",
                        "9. Author #1 – Track #1 14 pts Download DAT · 256 B"
                    )
                }

                findThird().isNotPresent
            }
        }
    }

    @Test
    fun testResultVisibilityAndDownloads() = test {
        var unpublishedCompoId: UUID? = null
        var publishedFileId: UUID? = null
        var unpublishedFileId: UUID? = null
        var unpublishedEntryId: UUID? = null

        setupServices {
            val app = this
            either {
                val published = compos.add(NewCompo("Published", "")).bind()
                val unpublished = compos.add(NewCompo("Secret", "")).bind()
                unpublishedCompoId = unpublished.id

                val user = addTestUser(app, "uploader").bind()
                addTestAdmin(app).bind()

                val entryA = entries.add(
                    NewEntry(
                        title = "Entry A",
                        author = "Author A",
                        file = FileUpload.createTestData("a.dat", 256),
                        compoId = published.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                val entryB = entries.add(
                    NewEntry(
                        title = "Entry B",
                        author = "Author B",
                        file = FileUpload.createTestData("b.dat", 256),
                        compoId = unpublished.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                unpublishedEntryId = entryB.id
                publishedFileId = files.getLatest(entryA.id, originalsOnly = true).bind().id
                unpublishedFileId = files.getLatest(entryB.id, originalsOnly = true).bind().id

                compos.publishResults(published.id, true).bind()
            }
        }

        // Anonymous visitors see only the published compo, with nothing dimmed
        // and no admin controls.
        it.get("/results") {
            assertEquals(1, findAll("article").size)
            findFirst("article") {
                findFirst("header") { text.toBe("Published compo") }
            }
            assertEquals(0, findAll("article").count { it.attribute("class").contains("unpublished") })
            // The admin publish toggle in the compo header must not render.
            // (findAll throws when nothing matches, which is the expected outcome here.)
            val headerButtons = runCatching { findAll("article header button") }.getOrDefault(emptyList())
            assertEquals(0, headerButtons.size)
        }

        // Anonymous downloads work exactly for compos with published results.
        assertEquals(HttpStatusCode.OK, it.client.get("/entries/download/$publishedFileId").status)
        assertEquals(HttpStatusCode.NotFound, it.client.get("/entries/download/$unpublishedFileId").status)

        // Preview assets of an unpublished compo respond 404 to anonymous
        // visitors (not a login redirect).
        assertEquals(
            HttpStatusCode.NotFound,
            it.client.get("/entries/$unpublishedEntryId/preview-thumbnail").status,
        )

        // Public results.txt only contains published compos.
        val publicTxt = it.client.get("/results.txt")
        assertEquals(HttpStatusCode.OK, publicTxt.status)
        val publicTxtBody = publicTxt.bodyAsText()
        assertContains(publicTxtBody, "Entry A")
        assertFalse(publicTxtBody.contains("Entry B"))

        // Admins see both compos; the unpublished one is dimmed and carries the
        // publish toggle in its header.
        it.login("admin", "password")
        it.get("/results") {
            assertEquals(2, findAll("article").size)
            val unpublishedArticles =
                findAll("article").filter { it.attribute("class").contains("unpublished") }
            assertEquals(1, unpublishedArticles.size)
            val unpublishedArticle = unpublishedArticles.first()
            assertEquals("Secret compo", unpublishedArticle.findFirst("header").text)
            assertEquals(1, unpublishedArticle.findAll("header button").size)
        }

        // The admin results.txt still contains everything.
        val adminTxt = it.client.get("/admin/compos/results.txt")
        assertEquals(HttpStatusCode.OK, adminTxt.status)
        assertContains(adminTxt.bodyAsText(), "Entry B")

        // Publishing through the on-page toggle endpoint makes the compo public.
        it.buttonClick("/admin/compos/$unpublishedCompoId/publishResults/true")
        it.get("/results") {
            assertEquals(0, findAll("article").count { it.attribute("class").contains("unpublished") })
        }
    }

    private fun simulatePoints(c: Int, userIndex: Int, entryIndex: Int): Int {
        if (entryIndex == userIndex) return 5
        return maxOf(1, (c + entryIndex * 2 + userIndex * 3) % (entryIndex + 1) % 5)
    }

    private fun equalResults(actual: String, vararg expected: String) {
        val actualTrimmed = actual
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val expectedJoined = expected.joinToString(" ")
        actualTrimmed.toBe(expectedJoined)
    }
}
