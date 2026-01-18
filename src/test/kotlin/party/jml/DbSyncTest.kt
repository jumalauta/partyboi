package party.jml

import arrow.core.raise.either
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.Test
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.messages.MessageType
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.sync.SyncedTable
import party.jml.partyboi.triggers.OpenCloseSubmitting
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DbSyncTest : PartyboiTester {
    @Test
    fun `Reading and writing back to same database does not change anything`() = test {
        setupServices {
            val app = this
            either {
                val testUser = addTestUser(app, "john", "password").bind()

                val demoCompo = app.compos.add(NewCompo("Demo", "Make a demo about it")).bind()
                val musicCompo = app.compos.add(NewCompo("Music", "Make a song about it")).bind()

                val demoEntry = app.entries.add(
                    NewEntry(
                        compoId = demoCompo.id,
                        title = "My Glorious Demo",
                        author = "John Doe / Jumalauta",
                        file = FileUpload.createTestData("demo.xxx", 1024),
                        screenComment = "Greez to world healerz!",
                        orgComment = "I love you!",
                        userId = testUser.id
                    )
                ).bind()

                app.events.add(
                    event = NewEvent(
                        name = "Deadline for demo compo",
                        startTime = Clock.System.now(),
                        endTime = Clock.System.now(),
                        visible = true
                    ),
                    actions = listOf(
                        OpenCloseSubmitting(demoCompo.id, false),
                    )
                ).bind()

                app.messages.sendMessage(
                    userId = testUser.id,
                    type = MessageType.INFO,
                    text = "Hello there!"
                ).bind()

                app.properties.store("TestValue", false, "1234").bind()

                app.screen.addSlide(
                    slideSet = "default",
                    slide = TextSlide(
                        title = "Hello, world!",
                        content = "Welcome to the party!",
                        variant = null
                    )
                ).bind()

                app.voteKeys.createTickets(10, "default").bind()
                val voteKeys = app.voteKeys.getAllVoteKeys().bind()
                app.voteKeys.registerTicket(testUser.id, voteKeys.first().key.id!!).bind()

                app.compos.allowSubmit(demoCompo.id, false).bind()
                app.compos.allowVoting(demoCompo.id, true).bind()

                app.votes.castVote(testUser.copy(votingEnabled = true), demoEntry.id, 3).bind()

                SyncedTable.entries.forEach { table ->
                    println("Testing: $table")

                    val export = app.sync.getTable(table).bind()
                    println("Exported data: ${Json.encodeToString(export)}")
                    assertNotEquals(export.data.size, 0, "Expected '$table' to contain data")

                    app.sync.putTable(export).bind()
                    println("Data written back\n")

                    val export2 = app.sync.getTable(table).bind()
                    assertEquals(export.data, export2.data)
                }
            }
        }
    }

}