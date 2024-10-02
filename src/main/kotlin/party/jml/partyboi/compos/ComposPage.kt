package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.NewCompo
import party.jml.partyboi.entries.renderForm
import party.jml.partyboi.entries.switchLink
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Page

object ComposPage {
    fun render(newCompoForm: Form<NewCompo>, compos: List<Compo>) =
        Page("Compos") {
            form(method = FormMethod.post, action = "/compos", encType = FormEncType.multipartFormData) {
                if (compos.isNotEmpty()) {
                    article {
                        header { +"Compos" }
                        table {
                            thead {
                                tr {
                                    th { +"Name" }
                                    th { +"Submitting"}
                                    th { +"Voting" }
                                }
                            }
                            tbody {
                                compos.forEach { compo ->
                                    tr {
                                        td { a(href = "/compos/${compo.id}") { +compo.name } }
                                        td {
                                            switchLink(
                                                toggled = compo.allowSubmit,
                                                labelOn = "Open",
                                                labelOff = "Closed",
                                                urlPrefix = "/compos/${compo.id}/setSubmit"
                                            )
                                        }
                                        td {
                                            switchLink(
                                                toggled = compo.allowVote,
                                                labelOn = "Open",
                                                labelOff = "Closed",
                                                urlPrefix = "/compos/${compo.id}/setVoting"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                article {
                    header { +"Add new compo" }
                    fieldSet { renderForm(newCompoForm) }
                    footer { submitInput { value = "Add" } }
                }
            }
        }
}