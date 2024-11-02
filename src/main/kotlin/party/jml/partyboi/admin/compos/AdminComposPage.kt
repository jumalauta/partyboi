package party.jml.partyboi.admin.compos

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.IconSet
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.toggleButton

object AdminComposPage {
    fun render(
        newCompoForm: Form<NewCompo>,
        compos: List<Compo>,
        entries: Map<Int, List<Entry>>,
        generalRules: Form<GeneralRules>,
    ) =
        Page("Compos") {
            h1 { +"Compos" }
            columns(if (compos.isNotEmpty()) {
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
                                            val count = entries.get(compo.id)?.size ?: 0
                                            +count.toString()
                                        }
                                        td(classes = "align-right") {
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
                }
            } else null, {
                dataForm("/admin/compos") {
                    article {
                        header { +"Add new compo" }
                        fieldSet { renderFields(newCompoForm) }
                        footer { submitInput { value = "Add" } }
                    }
                }

                article {
                    dataForm("/admin/compos/generalRules") {
                        fieldSet {
                            renderFields(generalRules)
                        }
                        footer { submitInput { value = "Save changes" } }
                    }
                }
            })
        }
}
