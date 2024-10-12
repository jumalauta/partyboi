package party.jml.partyboi.admin.compos

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.switchLink
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.IconSet
import party.jml.partyboi.templates.components.icon
import party.jml.partyboi.templates.components.toggleButton

object AdminEditCompoPage {
    fun render(compo: Form<Compo>, entries: List<Entry>) =
        Page("Edit compo") {
            val (qualified, nonQualified) = entries.partition { it.qualified }

            h1 { +"${compo.data.name} compo" }

            form(method = FormMethod.post, action = "/admin/compos/${compo.data.id}", encType = FormEncType.multipartFormData) {
                article {
                    fieldSet {
                        renderForm(compo)
                    }
                    footer {
                        submitInput { value = "Save changes" }
                    }
                }
            }

            if (qualified.isNotEmpty()) {
                article {
                    header { +"Qualified entries" }
                    table {
                        thead {
                            tr {
                                th {}
                                th { +"Title" }
                                th { +"Author" }
                                th { +"Submitted by" }
                                th {}
                            }
                        }
                        tbody(classes = "sortable") {
                            attributes.put("data-draggable", "tr")
                            attributes.put("data-handle", ".handle")
                            attributes.put("data-callback", "/admin/compos/${compo.data.id}/runOrder")
                            qualified.forEach { entry ->
                                tr {
                                    attributes.put("data-dragid", entry.id.toString())
                                    td(classes = "handle") { icon("arrows-up-down") }
                                    td { a(href = "/entries/${entry.id}") { +entry.title } }
                                    td { +entry.author }
                                    td { +"userId: ${entry.userId}" }
                                    td(classes = "align-right") {
                                        toggleButton(
                                            entry.qualified,
                                            IconSet.qualified,
                                            "/admin/compos/entries/${entry.id}/setQualified"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    footer {
                        nav {
                            ul {
                                li {
                                    a(href = "/admin/compos/${compo.data.id}/download") {
                                        attributes.put("role", "button")
                                        icon("download")
                                        +" Download files"
                                    }
                                }
                                li {
                                    a(href = "/admin/compos/${compo.data.id}/generate-slides") {
                                        attributes.put("role", "button")
                                        icon("tv")
                                        +" Generate slides"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (nonQualified.isNotEmpty()) {
                article {
                    header { +"Non-qualified entries" }
                    table {
                        thead {
                            tr {
                                th { +"Title" }
                                th { +"Author" }
                                th { +"Submitted by" }
                                th {}
                            }
                        }
                        tbody {
                            nonQualified.forEach { entry ->
                                tr {
                                    attributes.put("data-dragid", entry.id.toString())
                                    td { a(href = "/entries/${entry.id}") { +entry.title } }
                                    td { +entry.author }
                                    td { +"userId: ${entry.userId}" }
                                    td(classes = "align-right") {
                                        toggleButton(
                                            entry.qualified,
                                            IconSet.qualified,
                                            "/admin/compos/entries/${entry.id}/setQualified"
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