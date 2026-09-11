package party.jml

import arrow.core.raise.either
import org.junit.Test
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.FileFormat
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.sync.DuplicateKind
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconciliationTest : PartyboiTester {
    @Test
    fun `duplicate entries are detected and merged with higher points winning`() = test {
        setupServices {
            val app = this
            either {
                val alice = addTestUser(app, "alice").bind().copy(votingEnabled = true)
                val bob = addTestUser(app, "bob").bind().copy(votingEnabled = true)
                val carol = addTestUser(app, "carol").bind().copy(votingEnabled = true)

                val compoBase = app.compos.add(NewCompo("Demo", "Make a demo")).bind()
                app.compos.update(compoBase.copy(fileFormats = FileFormat.entries.toList())).bind()
                val compo = app.compos.getById(compoBase.id).bind()

                val original = app.entries.add(
                    NewEntry(
                        compoId = compo.id,
                        title = "Cool Demo",
                        author = "Alice / Group",
                        file = FileUpload.createTestData("cool.bin", 512),
                        screenComment = "",
                        orgComment = "",
                        userId = alice.id,
                    )
                ).bind()
                val duplicate = app.entries.add(
                    NewEntry(
                        compoId = compo.id,
                        title = "cool demo",
                        author = "alice / group",
                        file = FileUpload.createTestData("cool2.bin", 768),
                        screenComment = "",
                        orgComment = "",
                        userId = bob.id,
                    )
                ).bind()
                val unrelated = app.entries.add(
                    NewEntry(
                        compoId = compo.id,
                        title = "Something Else",
                        author = "Bob",
                        file = FileUpload.createTestData("other.bin", 300),
                        screenComment = "",
                        orgComment = "",
                        userId = bob.id,
                    )
                ).bind()

                app.compos.allowSubmit(compo.id, false).bind()
                app.compos.allowVoting(compo.id, true).bind()
                app.votes.castVote(carol, original.id, 3).bind()
                app.votes.castVote(carol, duplicate.id, 5).bind()
                app.votes.castVote(alice, duplicate.id, 4).bind()

                val candidates = app.reconciliation.duplicateEntryCandidates().bind()
                assertEquals(1, candidates.size)
                val pair = candidates.first()
                assertEquals(
                    setOf(original.id, duplicate.id),
                    setOf(pair.a.id, pair.b.id),
                )

                app.reconciliation.mergeEntries(original.id, duplicate.id).bind()

                assertTrue(app.entries.getById(duplicate.id).isLeft(), "loser entry should be deleted")
                app.entries.getById(original.id).bind()
                app.entries.getById(unrelated.id).bind()

                // Carol voted on both copies: higher points (5) win. Alice's vote follows the merge.
                val votes = app.votes.getAllVotes().bind()
                val survivorVotes = votes.filter { it.entryId == original.id }
                assertEquals(2, survivorVotes.size)
                assertEquals(5, survivorVotes.first { it.userId == carol.id }.points)
                assertEquals(4, survivorVotes.first { it.userId == alice.id }.points)
                assertEquals(2, votes.size)

                // Both files now belong to the survivor.
                assertEquals(2, app.files.getAllVersions(original.id).bind().size)

                assertEquals(0, app.reconciliation.duplicateEntryCandidates().bind().size)
            }
        }
        it.get("/") {}
    }

    @Test
    fun `entries with identical file contents are detected even with different titles`() = test {
        setupServices {
            val app = this
            either {
                val alice = addTestUser(app, "alice").bind()
                val bob = addTestUser(app, "bob").bind()

                val compoBase = app.compos.add(NewCompo("Demo", "Make a demo")).bind()
                app.compos.update(compoBase.copy(fileFormats = FileFormat.entries.toList())).bind()
                val compo = app.compos.getById(compoBase.id).bind()

                // createTestData produces zero-filled content, so equal sizes mean equal checksums.
                app.entries.add(
                    NewEntry(compo.id, "Cool Demo", "Alice", FileUpload.createTestData("a.bin", 512), "", "", alice.id)
                ).bind()
                app.entries.add(
                    NewEntry(compo.id, "Kewl Demo", "A.", FileUpload.createTestData("b.bin", 512), "", "", bob.id)
                ).bind()

                val candidates = app.reconciliation.duplicateEntryCandidates().bind()
                assertEquals(1, candidates.size)
                assertTrue(candidates.first().checksumMatch)
            }
        }
        it.get("/") {}
    }

    @Test
    fun `forked users are detected and merged`() = test {
        setupServices {
            val app = this
            either {
                val dave = addTestUser(app, "dave").bind().copy(votingEnabled = true)
                // The name the sync collision resolver would give dave's copy from the other instance
                val daveFork = addTestUser(app, "dave-ABC-DEF").bind().copy(votingEnabled = true)
                addTestUser(app, "erin").bind()

                val compoBase = app.compos.add(NewCompo("Demo", "Make a demo")).bind()
                app.compos.update(compoBase.copy(fileFormats = FileFormat.entries.toList())).bind()
                val compo = app.compos.getById(compoBase.id).bind()

                val entry = app.entries.add(
                    NewEntry(compo.id, "Forked Prod", "Dave", FileUpload.createTestData("f.bin", 100), "", "", daveFork.id)
                ).bind()
                val other = app.entries.add(
                    NewEntry(compo.id, "Other Prod", "Someone", FileUpload.createTestData("o.bin", 200), "", "", dave.id)
                ).bind()

                app.compos.allowSubmit(compo.id, false).bind()
                app.compos.allowVoting(compo.id, true).bind()
                app.votes.castVote(dave, entry.id, 2).bind()
                app.votes.castVote(daveFork, entry.id, 4).bind()
                app.votes.castVote(daveFork, other.id, 3).bind()

                val candidates = app.reconciliation.duplicateUserCandidates().bind()
                assertEquals(1, candidates.size)
                assertEquals(
                    setOf(dave.id, daveFork.id),
                    setOf(candidates.first().a.id, candidates.first().b.id),
                )

                app.reconciliation.mergeUsers(dave.id, daveFork.id).bind()

                assertTrue(
                    app.users.getUsers().bind().none { it.id == daveFork.id },
                    "forked user should be deleted"
                )

                // The fork's entry now belongs to dave.
                assertEquals(dave.id, app.entries.getById(entry.id).bind().userId)

                // Dave voted 2 and the fork 4 on the same entry: higher points win; the
                // fork's vote on the other entry follows the merge.
                val votes = app.votes.getAllVotes().bind()
                assertEquals(4, votes.first { it.userId == dave.id && it.entryId == entry.id }.points)
                assertEquals(3, votes.first { it.userId == dave.id && it.entryId == other.id }.points)
                assertEquals(2, votes.count { it.userId == dave.id })

                assertEquals(0, app.reconciliation.duplicateUserCandidates().bind().size)
            }
        }
        it.get("/") {}
    }

    @Test
    fun `dismissed candidates stop appearing`() = test {
        setupServices {
            val app = this
            either {
                val alice = addTestUser(app, "alice").bind()
                val bob = addTestUser(app, "bob").bind()

                val compoBase = app.compos.add(NewCompo("Demo", "Make a demo")).bind()
                app.compos.update(compoBase.copy(fileFormats = FileFormat.entries.toList())).bind()
                val compo = app.compos.getById(compoBase.id).bind()

                val e1 = app.entries.add(
                    NewEntry(compo.id, "Same Title", "Same Author", FileUpload.createTestData("a.bin", 100), "", "", alice.id)
                ).bind()
                val e2 = app.entries.add(
                    NewEntry(compo.id, "Same Title", "Same Author", FileUpload.createTestData("b.bin", 200), "", "", bob.id)
                ).bind()

                assertEquals(1, app.reconciliation.duplicateEntryCandidates().bind().size)

                // Dismissing works regardless of the order the ids are given in
                app.reconciliation.dismiss(DuplicateKind.Entry, e2.id, e1.id).bind()

                assertEquals(0, app.reconciliation.duplicateEntryCandidates().bind().size)
            }
        }
        it.get("/") {}
    }
}
