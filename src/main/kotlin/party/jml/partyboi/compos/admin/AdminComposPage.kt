package party.jml.partyboi.compos.admin

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.IconSet
import party.jml.partyboi.templates.components.buttonGroup
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.toggleButton

object AdminComposPage {
    fun render(
        newCompoForm: Form<NewCompo>,
        generalRulesForm: Form<GeneralRules>,
        compos: List<Compo>,
        entries: Map<Int, List<Entry>>,
    ) = Page(
        title = "Compos",
        subLinks = compos.map { it.toNavItem() },
    ) {
        h1 { +"Compos" }
        columns(
            if (compos.isNotEmpty()) {
                {
                    article {
                        table {
                            thead {
                                tr {
                                    th { +"Name" }
                                    th { +"Entries" }
                                    th {}
                                }
                            }
                            tbody {
                                compos.forEach { compo ->
                                    tr {
                                        td { a(href = "/admin/compos/${compo.id}") { +compo.name } }
                                        td {
                                            val count = entries[compo.id]?.size ?: 0
                                            +count.toString()
                                        }
                                        td(classes = "settings") {
                                            toggleButton(
                                                compo.visible,
                                                IconSet.visibility,
                                                "/admin/compos/${compo.id}/setVisible"
                                            )
                                            toggleButton(
                                                compo.allowSubmit,
                                                IconSet.submitting,
                                                "/admin/compos/${compo.id}/setSubmit"
                                            )
                                            toggleButton(
                                                compo.allowVote,
                                                IconSet.voting,
                                                "/admin/compos/${compo.id}/setVoting"
                                            )
                                            toggleButton(
                                                compo.publicResults,
                                                IconSet.resultsPublic,
                                                "/admin/compos/${compo.id}/publishResults"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    buttonGroup {
                        a(href = "/admin/compos/results.txt") {
                            role = "button"
                            +"Get results.txt"
                        }
                        a(href = "/admin/compos/entries.zip") {
                            role = "button"
                            +"Download entries for distribution"
                        }
                    }
                }
            } else null) {

            renderForm(
                title = "Add new compo",
                url = "/admin/compos",
                form = newCompoForm,
                submitButtonLabel = "Add",
            )

            renderForm(
                url = "/admin/compos/generalRules",
                form = generalRulesForm,
            )
        }
    }
}
