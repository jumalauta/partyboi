package party.jml.partyboi.admin.compos

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.IconSet
import party.jml.partyboi.templates.components.toggleButton

object AdminComposPage {
    fun render(newCompoForm: Form<NewCompo>, compos: List<Compo>, entries: Map<Int, List<Entry>>) =
        Page("Compos") {
            if (compos.isNotEmpty()) {
                article {
                    header { +"Compos" }
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
                                        toggleButton(compo.visible, IconSet.visibility, "/admin/compos/${compo.id}/setVisible")
                                        toggleButton(compo.allowSubmit, IconSet.submitting, "/admin/compos/${compo.id}/setSubmit")
                                        toggleButton(compo.allowVote, IconSet.voting, "/admin/compos/${compo.id}/setVoting")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            form(method = FormMethod.post, action = "/admin/compos", encType = FormEncType.multipartFormData) {
                article {
                    header { +"Add new compo" }
                    fieldSet { renderForm(newCompoForm) }
                    footer { submitInput { value = "Add" } }
                }
            }
        }
}