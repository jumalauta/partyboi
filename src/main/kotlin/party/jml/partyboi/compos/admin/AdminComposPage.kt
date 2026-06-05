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
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.toggleButton
import java.util.*

object AdminComposPage {
    fun render(
        newCompoForm: Form<NewCompo>,
        generalRulesForm: Form<GeneralRules>,
        compos: List<Compo>,
        entries: Map<UUID, List<Entry>>,
        manualResultCounts: Map<UUID, Int>,
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
                                    val count = if (compo.manualResults) {
                                        manualResultCounts[compo.id] ?: 0
                                    } else {
                                        entries[compo.id]?.size ?: 0
                                    }
                                    // Land on the tab that has content, otherwise Settings.
                                    val href = when {
                                        count == 0 -> "/admin/compos/${compo.id}"
                                        compo.manualResults -> "/admin/compos/${compo.id}/results"
                                        else -> "/admin/compos/${compo.id}/entries"
                                    }
                                    tr {
                                        td { a(href = href) { +compo.name } }
                                        td { +count.toString() }
                                        td(classes = "settings") {
                                            toggleButton(
                                                compo.visible,
                                                IconSet.visibility,
                                                "/admin/compos/${compo.id}/setVisible"
                                            )
                                            toggleButton(
                                                compo.allowSubmit,
                                                IconSet.submitting,
                                                "/admin/compos/${compo.id}/setSubmit",
                                                disabled = compo.manualResults
                                            )
                                            toggleButton(
                                                compo.allowVote,
                                                IconSet.voting,
                                                "/admin/compos/${compo.id}/setVoting",
                                                disabled = compo.manualResults
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
                    details(classes = "dropdown") {
                        summary {
                            attributes["role"] = "button"
                            +"Download"
                        }
                        ul {
                            li {
                                a(href = "/admin/compos/results.txt") {
                                    +"Results"
                                }
                            }
                            li {
                                a(href = "/admin/compos/results-with-info.txt") {
                                    +"Results with info screen comments"
                                }
                            }
                            li {
                                a(href = "/admin/compos/entries.zip") {
                                    +"All entries for scene.org"
                                }
                            }
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
