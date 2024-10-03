package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.templates.Page

object ComposPage {
    fun render(compos: List<Compo>) =
        Page("Compos") {
            compos.forEach { compo ->
                article {
                    header { +compo.name }
                    p { +(if (compo.rules.isEmpty()) "Rules are missing... :-(" else compo.rules) }
                    footer {
                        if (compo.allowSubmit) {
                            a(href = "/submit") {
                                role = "button"
                                +"Submit an entry"
                            }
                        }
                        if (compo.allowVote) {
                            a(href = "/vote") {
                                role = "button"
                                +"Vote entries"
                            }
                        }
                    }
                }
            }
        }
}