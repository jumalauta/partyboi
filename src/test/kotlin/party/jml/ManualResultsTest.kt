package party.jml

import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.compos.NewManualResult
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.form.FileUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualResultsTest : PartyboiTester {

    // Regression: a manual-results compo that still had qualified entry rows had those entries counted
    // by the vote-results query AND its manual rows by the manual-results query, so each compo showed
    // up twice and groupResults mixed position-0 vote rows into the manual standings. The vote-results
    // query must exclude manual-results compos.
    @Test
    fun testManualResultsCompoIsNotDoubleCounted() = test {
        var appRef: AppServices? = null
        var compoId = UUIDv7.Empty

        setupServices {
            val app = this
            appRef = app
            either {
                val user = addTestUser(app).bind()
                val compo = compos.add(NewCompo("Best Photo", "")).bind()
                compoId = compo.id

                // A qualified entry exists (e.g. it was submitted before the compo went manual).
                val entry = entries.add(
                    NewEntry(
                        title = "Submitted Entry",
                        author = "Bob",
                        file = FileUpload.createTestData("photo.dat", 256),
                        compoId = compo.id,
                        screenComment = "",
                        orgComment = "",
                        userId = user.id,
                    )
                ).bind()
                entries.setQualified(entry.id, true).bind()

                // The compo is switched to manually-entered results, and a result is added.
                compos.update(compo.copy(manualResults = true)).bind()
                manualResults.add(
                    NewManualResult(
                        title = "Winner",
                        author = "Alice",
                        scoreText = "42 pts",
                        screenComment = "",
                        compoId = compo.id,
                    )
                ).bind()
            }
        }

        it.login()

        val results = appRef!!.votes.getResults().getOrNull().orEmpty().filter { it.compoId == compoId }
        assertTrue(
            results.all { it.isManual },
            "a manual-results compo must not include vote-derived entry rows: $results",
        )
        assertEquals(
            listOf("Winner"),
            results.map { it.title },
            "only the manual result should be listed for a manual-results compo",
        )
    }
}
