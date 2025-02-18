package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.components.markdown

object ComposPage {
    fun render(generalRules: GeneralRules, compos: List<Compo>) =
        Page("Compos") {
            h1 { +"Compos" }

            if (generalRules.rules.isNotEmpty()) {
                article {
                    id = "generalRules"
                    cardHeader("General rules")
                    markdown(generalRules.rules)
                }
            }

            if (compos.isEmpty()) {
                article {
                    id = "noCompos"
                    +"No compos have not been published."
                }
            }

            compos.filter { it.visible }.forEach { compo ->
                article(classes = "compo") {
                    cardHeader(compo.name)
                    markdown(compo.rules)

                    nav {
                        ul {
                            if (compo.allowSubmit) {
                                li {
                                    a(href = "/entries/submit/${compo.id}", classes = "submitEntry") {
                                        +"Submit an entry"
                                    }
                                }
                            }
                            if (compo.allowVote) {
                                li {
                                    a(href = "/vote", classes = "vote") {
                                        +"Vote"
                                    }
                                }
                            }
                            if (compo.publicResults) {
                                li {
                                    a(href = "/results#${compo.id}", classes = "results") {
                                        +"Results"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}