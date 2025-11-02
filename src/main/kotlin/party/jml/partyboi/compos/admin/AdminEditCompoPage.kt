package party.jml.partyboi.compos.admin

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.isFalse
import party.jml.partyboi.data.isTrue
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.FileFormat
import party.jml.partyboi.entries.FileFormatCategory
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*

object AdminEditCompoPage {
    fun render(
        compoForm: Form<Compo>,
        entries: List<Entry>,
        compos: List<Compo>,
    ) = Page(
        title = "Edit compo",
        subLinks = compos.map { it.toNavItem() },
    ) {
        val (qualified, nonQualified) = entries.partition { it.qualified }

        h1 { +"${compoForm.data.name} compo" }

        columns(
            {
                dataForm("/admin/compos/${compoForm.data.id}") {
                    article {
                        fieldSet {
                            renderFields(compoForm)
                        }

                        fieldSet {
                            p { +"File uploads" }
                            radioInput(name = "requireFile") {
                                checked = compoForm.data.requireFile.isTrue()
                                value = "true"
                                label { +"Required" }
                            }
                            radioInput(name = "requireFile") {
                                checked = compoForm.data.requireFile.isNone()
                                value = "none"
                                label { +"Optional" }
                            }
                            radioInput(name = "requireFile") {
                                checked = compoForm.data.requireFile.isFalse()
                                value = "false"
                                label { +"File uploads disabled" }
                            }
                        }

                        fieldSet {
                            label {
                                p { +"Accepted file formats" }
                                FileFormatCategory.entries.forEach { cat ->
                                    details {
                                        val formats = FileFormat.entries.filter { it.category == cat }
                                        if (formats
                                                .map { it.name }
                                                .intersect(compoForm.data.fileFormats.map { it.name })
                                                .isNotEmpty()
                                        ) {
                                            attributes["open"] = ""
                                        }
                                        summary { +cat.description }
                                        ul {
                                            formats.forEach { format ->
                                                li {
                                                    input(name = "fileFormats") {
                                                        type = InputType.checkBox
                                                        value = format.name
                                                        checked =
                                                            compoForm.data.fileFormats.find { it.name == format.name } != null
                                                        +format.description
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        submitButton("Save changes")
                    }
                }

                article {
                    header { +"State" }

                    table {
                        tbody {
                            tr {
                                td(classes = "narrow center") { icon("eye") }
                                td {
                                    switchLink(
                                        compoForm.data.visible,
                                        "Everyone can see this compo",
                                        "This compo is hidden",
                                        "/admin/compos/${compoForm.data.id}/setVisible"
                                    )
                                }
                            }
                            tr {
                                td(classes = "narrow center") { icon("file-arrow-up") }
                                td {
                                    switchLink(
                                        compoForm.data.allowSubmit,
                                        "Users can submit and update entries",
                                        "Users cannot submit and update entries",
                                        "/admin/compos/${compoForm.data.id}/setSubmit",
                                        compoForm.data.allowVote
                                    )
                                }
                            }
                            tr {
                                td(classes = "narrow center") { icon("check-to-slot") }
                                td {
                                    switchLink(
                                        compoForm.data.allowVote,
                                        "Users can vote the entries of this compo",
                                        "Users cannot vote the entries of this compo",
                                        "/admin/compos/${compoForm.data.id}/setVoting",
                                        compoForm.data.allowSubmit
                                    )
                                }
                            }
                            tr {
                                td(classes = "narrow center") { icon("square-poll-horizontal") }
                                td {
                                    switchLink(
                                        compoForm.data.publicResults,
                                        "Everyone can see the results of this compo",
                                        "The results of this compo are hidden",
                                        "/admin/compos/${compoForm.data.id}/publishResults"
                                    )
                                }
                            }
                        }
                    }
                }

            }, if (qualified.isNotEmpty() || nonQualified.isNotEmpty()) {
                {
                    if (qualified.isNotEmpty()) {
                        article {
                            header { +"Qualified entries" }

                            table {
                                thead {
                                    tr {
                                        th(classes = "narrow") {}
                                        th { +"Title" }
                                        th { +"Author" }
                                        th(classes = "settings") {}
                                    }
                                }
                                tbody(classes = "sortable") {
                                    attributes.put("data-draggable", "tr")
                                    attributes.put("data-handle", ".handle")
                                    attributes.put("data-callback", "/admin/compos/${compoForm.data.id}/runOrder")
                                    qualified.forEach { entry ->
                                        tr {
                                            attributes.put("data-dragid", entry.id.toString())
                                            td(classes = "handle") { icon("arrows-up-down") }
                                            td { a(href = "/entries/${entry.id}") { +entry.title } }
                                            td { +entry.author }
                                            td(classes = "settings") {
                                                toggleButton(
                                                    entry.qualified,
                                                    IconSet.qualified,
                                                    "/admin/compos/entries/${entry.id}/setQualified"
                                                )
                                                toggleButton(
                                                    entry.allowEdit,
                                                    IconSet.allowEdit,
                                                    "/admin/compos/entries/${entry.id}/allowEdit"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            small {
                                +"Set run order by dragging entries by "
                                icon("arrows-up-down")
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
                                        th(classes = "narrow") { +"Q." }
                                    }
                                }
                                tbody {
                                    nonQualified.forEach { entry ->
                                        tr {
                                            attributes.put("data-dragid", entry.id.toString())
                                            td { a(href = "/entries/${entry.id}") { +entry.title } }
                                            td { +entry.author }
                                            td(classes = "settings") {
                                                toggleButton(
                                                    entry.qualified,
                                                    IconSet.qualified,
                                                    "/admin/compos/entries/${entry.id}/setQualified"
                                                )
                                                toggleButton(
                                                    entry.allowEdit,
                                                    IconSet.allowEdit,
                                                    "/admin/compos/entries/${entry.id}/allowEdit"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    buttonGroup {
                        a(href = "/admin/compos/${compoForm.data.id}/download", classes = "osSpecific") {
                            attributes.put("role", "button")
                            icon("download")
                            br {}
                            +"Download files"
                        }
                        a(href = "/admin/compos/${compoForm.data.id}/generate-result-slides") {
                            attributes.put("role", "button")
                            icon("square-poll-horizontal")
                            br {}
                            +"Result slides"
                        }
                    }
                }
            } else null)
        script(src = "/assets/draggable.min.js") {}
    }
}