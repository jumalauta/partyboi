package party.jml

import arrow.core.Either
import arrow.core.raise.either
import it.skrape.matchers.toBe
import it.skrape.matchers.toBeEmpty
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.AppError
import kotlin.test.Test

class CompoPageTest : PartyboiTester {
    @Test
    fun testEmptyCompoPage() = test {
        services { resetCompos(this, addCompos = false) }

        it.get("/compos") {
            this.findAll("#generalRules").toBeEmpty
            this.findFirst("#noCompos") { text.toBe("No compos have not been published.") }
        }
    }

    @Test
    fun testCompoPageForSubmitting() = test {
        services {
            val self = this
            either {
                resetCompos(self).bind()
                compos.setGeneralRules(GeneralRules("Make no harm.")).bind()
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
        services {
            val self = this
            either {
                val demoCompoId = resetCompos(self).bind()
                compos.allowSubmit(demoCompoId!!, false)
            }
        }

        it.get("/compos") {
            findFirst("article.compo .submitEntry").isNotPresent
        }
    }

    fun testCompoPageWhenVotingOpened() = test {
        services {
            val self = this
            either {
                val demoCompoId = resetCompos(self).bind()
                compos.allowSubmit(demoCompoId!!, false)
                compos.allowVoting(demoCompoId, true)
            }
        }

        it.get("/compos") {
            findFirst("article.compo .vote") { text.toBe("Vote") }
        }
    }

    fun testCompoPageWhenResultsPublished() = test {
        services {
            val self = this
            either {
                val demoCompoId = resetCompos(self).bind()
                compos.allowSubmit(demoCompoId!!, false)
                compos.publishResults(demoCompoId, true)
            }
        }

        it.get("/compos") {
            findFirst("article.compo .results") { text.toBe("Results") }
        }
    }

    private fun resetCompos(app: AppServices, addCompos: Boolean = true): Either<AppError, Int?> = either {
        app.compos.deleteAll().bind()
        app.compos.setGeneralRules(GeneralRules("")).bind()
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