package party.jml

import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.compos.admin.ResultsStep
import party.jml.partyboi.data.UUIDv7
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultsPublishingTest : PartyboiTester {

    // Regression: advancing the on-screen results presentation to its final "End" step published the
    // results and made the compo visible but left voting open, so voters could keep changing votes on
    // a compo whose standings were already on the big screen. Publishing must also close voting.
    @Test
    fun testPublishingResultsClosesVoting() = test {
        var appRef: AppServices? = null
        var compoId = UUIDv7.Empty

        setupServices {
            appRef = this
            either {
                val compo = compos.add(NewCompo("Demo", "")).bind()
                compoId = compo.id
                compos.allowSubmit(compo.id, false).bind()
                compos.allowVoting(compo.id, true).bind() // voting is open before results are shown
            }
        }

        it.login()

        // Advance the results presentation to the final step (results published on screen).
        ResultsStep.End("Demo", compoId).activate(appRef!!)

        val compo = appRef!!.compos.getById(compoId).getOrNull()!!
        assertFalse(compo.allowVote, "publishing results must close voting")
        assertTrue(compo.publicResults, "results must be published")
        assertTrue(compo.visible, "compo must be made visible")
    }

    // Voting must also be closed as soon as the results presentation is started (the standings are
    // frozen before they are computed), not only at the final publish step.
    @Test
    fun testStartingResultsPresentationClosesVoting() = test {
        var appRef: AppServices? = null
        var compoId = UUIDv7.Empty

        setupServices {
            appRef = this
            either {
                val compo = compos.add(NewCompo("Demo", "")).bind()
                compoId = compo.id
                compos.allowSubmit(compo.id, false).bind()
                compos.allowVoting(compo.id, true).bind() // voting is open before the presentation starts
            }
        }

        it.login()

        val compo = appRef!!.compos.getById(compoId).getOrNull()!!
        appRef!!.resultsRun.initResultsSteps(compo).getOrNull()

        assertFalse(
            appRef!!.compos.getById(compoId).getOrNull()!!.allowVote,
            "starting the results presentation must close voting",
        )
    }
}
