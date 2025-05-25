package party.jml

import arrow.core.Either
import arrow.core.raise.either
import it.skrape.matchers.toBe
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.AppError
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.settings.AutomaticVoteKeys
import kotlin.test.Test

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
        var entryId = -1

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
                    .joinToString("") { if (it.hasAttribute("checked")) "x" else "." }
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
            findFirst("article") { text.toBe("Live voting for Demo compo begins soon!") }
        }
        it.buttonClickFails("/vote/${entry!!.id}/3")

        // An entry is shown
        app!!.votes.addEntryToLiveVoting(entry!!)
        it.get("/vote") {
            findFirst("article") {
                text.toBe("Live: Demo # Author – Entry 1 2 3 4 5 1. Friction – fr004: action 1 2 3 4 5")
            }
        }
        it.buttonClick("/vote/${entry!!.id}/3")
    }

    private fun setupCompo(app: AppServices): Either<AppError, Pair<Compo, Entry>> = either {
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
                file = FileUpload.Empty,
                compoId = demoCompo.id,
                screenComment = "",
                orgComment = "TODO()",
                userId = user.id,
            )
        ).bind()

        Pair(demoCompo, entry)
    }
}