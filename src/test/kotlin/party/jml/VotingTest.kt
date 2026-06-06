package party.jml

import arrow.core.raise.either
import it.skrape.matchers.toBe
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.voting.VoteService
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VotingTest : PartyboiTester {
    @Test
    fun testVotingNotEnabled() = test {
        setupServices {
            val app = this
            either {
                settings.automaticVoteKeys.set(AutomaticVoteKeys.PER_USER).bind()
                setupCompo(app).bind()
            }
        }

        it.login()
        it.get("/vote") {
            findFirst("article") { text.toBe("Nothing to vote at the moment.") }
        }
    }

    @Test
    fun testVotingEnabled() = test {
        var entryId = UUIDv7.Empty

        setupServices {
            val app = this
            either {
                settings.automaticVoteKeys.set(AutomaticVoteKeys.PER_USER).bind()
                val (compo, entry) = setupCompo(app).bind()
                compos.allowVoting(compo.id, true).bind()
                entryId = entry.id
            }
        }

        // Test that the voting form is visible
        it.login()
        it.get("/vote") {
            findFirst("article") {
                text.toBe("Demo # Author – Entry 1 2 3 4 5 1. Friction – fr004: action 1 2 3 4 5")
            }
        }

        // Test that vote casting works
        it.buttonClick("/vote/$entryId/4")
        it.get("/vote") {
            findFirst("article") {
                findAll("input")
                    .joinToString("") { elem -> if (elem.hasAttribute("checked")) "x" else "." }
                    .toBe("...x.")
            }
        }

        // Casting invalid points fails
        it.buttonClickFails("/vote/$entryId/-1")
        it.buttonClickFails("/vote/$entryId/6")
        it.buttonClickFails("/vote/$entryId/foo")
        it.buttonClickFails("/vote/9999/4")
    }

    @Test
    fun testLiveVoting() = test {
        var entry: Entry? = null
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                settings.automaticVoteKeys.set(AutomaticVoteKeys.PER_USER)
                addTestAdmin(app).bind()
                val (compo, testEntry) = setupCompo(app).bind()
                compos.allowSubmit(compo.id, false).bind()
                runBlocking { votes.startLiveVoting(compo.id) }
                entry = testEntry
            }
        }

        it.login("admin")

        // Live voting is active but no entries can be voted yet
        it.get("/vote") {
            findFirst("article") { text.toBe("Live voting for Demo compo begins soon.") }
        }
        it.buttonClickFails("/vote/${entry!!.id}/3")

        // An entry is shown
        app!!.votes.addEntryToLiveVoting(entry)
        it.get("/vote") {
            findFirst("article") {
                text.toBe("Live: Demo # Author – Entry 1 2 3 4 5 1. Friction – fr004: action 1 2 3 4 5")
            }
        }
        it.buttonClick("/vote/${entry.id}/3")
    }

    @Test
    fun testVotingHidesAuthor() = test {
        setupServices {
            val app = this
            either {
                settings.automaticVoteKeys.set(AutomaticVoteKeys.PER_USER).bind()
                val submitter = addTestUser(app, "submitter").bind()

                val hidden = compos.add(NewCompo("Hidden", "")).bind()
                compos.update(compos.getById(hidden.id).bind().copy(hideAuthor = true)).bind()
                compos.setVisible(hidden.id, true).bind()
                compos.allowSubmit(hidden.id, false).bind()
                compos.allowVoting(hidden.id, true).bind()
                entries.add(
                    NewEntry(
                        title = "Secret Title",
                        author = "SecretAuthor",
                        file = FileUpload.createTestData("secret.dat", 256),
                        compoId = hidden.id,
                        screenComment = "",
                        orgComment = "",
                        userId = submitter.id,
                    )
                ).bind()

                val visible = compos.add(NewCompo("Visible", "")).bind()
                compos.setVisible(visible.id, true).bind()
                compos.allowSubmit(visible.id, false).bind()
                compos.allowVoting(visible.id, true).bind()
                entries.add(
                    NewEntry(
                        title = "Public Title",
                        author = "PublicAuthor",
                        file = FileUpload.createTestData("public.dat", 256),
                        compoId = visible.id,
                        screenComment = "",
                        orgComment = "",
                        userId = submitter.id,
                    )
                ).bind()
            }
        }

        it.login()
        it.get("/vote") {
            relaxed = true
            assertContains(text, "PublicAuthor")
            assertContains(text, "Public Title")
            assertContains(text, "Secret Title")
            assertFalse(
                text.contains("SecretAuthor"),
                "Author must not appear on /vote when compo has hideAuthor=true (got: $text)",
            )
        }
    }

    @Test
    fun testParticipationGrantsMeanPoints() = test {
        var entryIds = emptyList<java.util.UUID>()
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                settings.automaticVoteKeys.set(AutomaticVoteKeys.PER_USER).bind()
                val (_, entries) = setupCompoWithEntries(app, count = 3).bind()
                entryIds = entries.map { it.id }
                val voter = addTestUser(app, "voter").bind()
                votes.castVote(voter, entries[0].id, 5).bind()
            }
        }

        // Touch the app over HTTP so `setupServices`' `application {}` block runs and `app` is set.
        it.login()
        val results = runBlocking { app!!.votes.getResults() }.getOrNull()!!
        val byEntry = results.associateBy { it.entryId }
        assertEquals(5, byEntry[entryIds[0]]?.points, "voted entry keeps its explicit points")
        assertEquals(VoteService.MEAN_POINTS, byEntry[entryIds[1]]?.points, "skipped entry gets MEAN_POINTS")
        assertEquals(VoteService.MEAN_POINTS, byEntry[entryIds[2]]?.points, "skipped entry gets MEAN_POINTS")
    }

    @Test
    fun testNoVotesYieldsZero() = test {
        var entryIds = emptyList<java.util.UUID>()
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                settings.automaticVoteKeys.set(AutomaticVoteKeys.PER_USER).bind()
                val (_, entries) = setupCompoWithEntries(app, count = 3).bind()
                entryIds = entries.map { it.id }
                addTestUser(app, "lurker").bind()
            }
        }

        it.login()
        val results = runBlocking { app!!.votes.getResults() }.getOrNull()!!
        val byEntry = results.associateBy { it.entryId }
        entryIds.forEach { id ->
            assertEquals(0, byEntry[id]?.points, "no participation → no implicit points")
        }
    }

    private suspend fun setupCompoWithEntries(app: AppServices, count: Int): AppResult<Pair<Compo, List<Entry>>> = either {
        val compo = app.compos.add(NewCompo("Demo", "")).bind()
        app.compos.setVisible(compo.id, true).bind()
        app.compos.allowSubmit(compo.id, false).bind()
        val submitter = addTestUser(app, "submitter").bind()
        val entries = (1..count).map { i ->
            app.entries.add(
                NewEntry(
                    title = "Entry $i",
                    author = "Author $i",
                    file = FileUpload.createTestData("entry$i.dat", 256),
                    compoId = compo.id,
                    screenComment = "",
                    orgComment = "",
                    userId = submitter.id,
                )
            ).bind()
        }
        app.compos.allowVoting(compo.id, true).bind()
        Pair(compo, entries)
    }

    private suspend fun setupCompo(app: AppServices): AppResult<Pair<Compo, Entry>> = either {
        val demoCompo = app.compos.add(NewCompo("Demo", "")).bind()
        app.compos.setVisible(demoCompo.id, true).bind()
        app.compos.allowSubmit(demoCompo.id, false).bind()

        val musicCompo = app.compos.add(NewCompo("Music", "")).bind()
        app.compos.setVisible(musicCompo.id, true).bind()

        val user = addTestUser(app).bind()
        val entry = app.entries.add(
            NewEntry(
                title = "fr004: action",
                author = "Friction",
                file = FileUpload.createTestData("friction.dat", 256),
                compoId = demoCompo.id,
                screenComment = "",
                orgComment = "TODO()",
                userId = user.id,
            )
        ).bind()

        Pair(demoCompo, entry)
    }
}