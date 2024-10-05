package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.templates.Page

object ComposPage {
    fun render(compos: List<Compo>) =
        Page("Compos") {
            compos.filter { it.visible }.forEach { compo ->
                article {
                    header { +compo.name }
                    p { +compo.rules }
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