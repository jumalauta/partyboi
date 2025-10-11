package party.jml

import arrow.core.raise.either
import it.skrape.matchers.toBe
import it.skrape.selects.html5.article
import it.skrape.selects.text
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.settings.AutomaticVoteKeys
import kotlin.test.Test

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
                            screenComment = "",
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
                        findAll("td").text,
                        "1. Author #5 Demo #5 29",
                        "2. Author #4 Demo #4 26",
                        "Author #9 Demo #9 26",
                        "4. Author #8 Demo #8 24",
                        "5. Author #6 Demo #6 22",
                        "6. Author #3 Demo #3 19",
                        "Author #7 Demo #7 19",
                        "8. Author #0 Demo #0 14",
                        "Author #1 Demo #1 14",
                        "Author #2 Demo #2 14"
                    )
                }

                findSecond {
                    findFirst("header") { text.toBe("Music compo") }
                    equalResults(
                        findAll("td").text,
                        "1. Author #6 Track #6 25",
                        "2. Author #4 Track #4 24",
                        "Author #9 Track #9 24",
                        "4. Author #2 Track #2 23",
                        "Author #3 Track #3 23",
                        "Author #7 Track #7 23",
                        "7. Author #8 Track #8 22",
                        "8. Author #5 Track #5 18",
                        "9. Author #0 Track #0 14",
                        "Author #1 Track #1 14"
                    )
                }

                findThird().isNotPresent
            }
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