package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.markdown

object ComposPage {
    fun render(generalRules: GeneralRules, compos: List<Compo>) =
        Page("Compos") {
            h1 { +"Compos" }

            if (generalRules.rules.isNotEmpty()) {
                article {
                    header { +"General rules" }
                    markdown(generalRules.rules)
                }
            }

            compos.filter { it.visible }.forEach { compo ->
                article {
                    header { +compo.name }
                    markdown(compo.rules)
                    if (compo.allowSubmit) {
                        a(href = "/entries/submit/${compo.id}") {
                            +"Submit an entry"
                        }
                    }
                    if (compo.allowVote) {
                        a(href = "/vote") {
                            +"Vote"
                        }
                    }
                }
            }
        }
}