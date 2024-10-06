package party.jml.partyboi.admin.compos

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.switchLink
import party.jml.partyboi.templates.Page

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
                                th { +"Qualified" }
                            }
                        }
                        tbody(classes = "sortable") {
                            attributes.put("data-draggable", "tr")
                            attributes.put("data-handle", ".handle")
                            attributes.put("data-callback", "/admin/compos/${compo.data.id}/runOrder")
                            entries.forEach { entry ->
                                tr {
                                    attributes.put("data-dragid", entry.id.toString())
                                    td(classes = "handle") { +entry.title }
                                    td(classes = "handle") { +entry.author }
                                    td {
                                        switchLink(
                                            toggled = entry.qualified,
                                            labelOn = "Yes",
                                            labelOff = "No",
                                            urlPrefix = "/admin/compos/entries/${entry.id}/setQualified"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            script(src = "/assets/draggable.min.js") {}
        }
}