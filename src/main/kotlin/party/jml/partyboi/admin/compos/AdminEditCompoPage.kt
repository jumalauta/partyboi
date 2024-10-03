package party.jml.partyboi.admin.compos

import party.jml.partyboi.templates.Page
import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.Entry
import party.jml.partyboi.entries.renderForm
import party.jml.partyboi.entries.switchLink
import party.jml.partyboi.form.Form

object AdminEditCompoPage {
    fun render(compo: Form<Compo>, entries: List<Entry>) =
        Page("Edit compo") {
            form(method = FormMethod.post, action = "/admin/compos/${compo.data.id}", encType = FormEncType.multipartFormData) {
                article {
                    header { +"Edit '${compo.data.name}' compo" }
                    fieldSet {
                        renderForm(compo)
                    }
                    footer {
                        submitInput { value = "Save changes" }
                    }
                }

                article {
                    header { +"Entries" }
                    table {
                        thead {
                            tr {
                                th { +"Title" }
                                th { +"Author" }
                                th { +"State" }
                            }
                        }
                        tbody {
                            entries.forEach { entry ->
                                tr {
                                    td { +entry.title }
                                    td { +entry.author }
                                    td {
                                        switchLink(
                                            toggled = entry.qualified,
                                            labelOn = "OK",
                                            labelOff = "Disqualified",
                                            urlPrefix = "/admin/compos/entries/${entry.id}/setQualified"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}