package party.jml

import arrow.core.raise.either
import it.skrape.matchers.toBe
import it.skrape.matchers.toBeEmpty
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.system.AppResult
import kotlin.test.Test

class CompoPageTest : PartyboiTester {
    @Test
    fun testEmptyCompoPage() = test {
        setupServices { setupCompos(this, addCompos = false) }

        it.get("/compos") {
            this.findAll("#generalRules").toBeEmpty
            this.findFirst("#noCompos") { text.toBe("No compos have not been published.") }
        }
    }

    @Test
    fun testCompoPageForSubmitting() = test {
        setupServices {
            val self = this
            either {
                setupCompos(self).bind()
                compos.generalRules.set(GeneralRules("Make no harm.")).bind()
            }
        }

        it.get("/compos") {
            findFirst("#generalRules") { text.toBe("General rules Make no harm.") }
            findFirst("article.compo") {
                findFirst("header") { text.toBe("Demo") }
                findFirst("div") { text.toBe("Make a demo about it") }
                findFirst(".submitEntry") { text.toBe("Submit an entry") }
                findFirst(".vote").isNotPresent
                findFirst(".results").isNotPresent
            }
            findSecond("article.compo").isNotPresent
        }
    }

    fun testCompoPageAfterSubmittingClosed() = test {
        setupServices {
            val self = this
            either {
                val demoCompoId = setupCompos(self).bind()
                compos.allowSubmit(demoCompoId!!, false)
            }
        }

        it.get("/compos") {
            findFirst("article.compo .submitEntry").isNotPresent
        }
    }

    fun testCompoPageWhenVotingOpened() = test {
        setupServices {
            val self = this
            either {
                val demoCompoId = setupCompos(self).bind()
                compos.allowSubmit(demoCompoId!!, false)
                compos.allowVoting(demoCompoId, true)
            }
        }

        it.get("/compos") {
            findFirst("article.compo .vote") { text.toBe("Vote") }
        }
    }

    fun testCompoPageWhenResultsPublished() = test {
        setupServices {
            val self = this
            either {
                val demoCompoId = setupCompos(self).bind()
                compos.allowSubmit(demoCompoId!!, false)
                compos.publishResults(demoCompoId, true)
            }
        }

        it.get("/compos") {
            findFirst("article.compo .results") { text.toBe("Results") }
        }
    }

    private suspend fun setupCompos(app: AppServices, addCompos: Boolean = true): AppResult<Int?> = either {
        if (addCompos) {
            val demoCompo = app.compos.add(NewCompo("Demo", "Make a demo about it")).bind()
            app.compos.setVisible(demoCompo.id, true).bind()
            app.compos.add(NewCompo("Secret", "This will be reveled maybe later")).bind()
            demoCompo.id
        } else {
            null
        }
    }
}