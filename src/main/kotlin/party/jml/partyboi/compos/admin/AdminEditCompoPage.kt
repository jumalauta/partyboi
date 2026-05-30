package party.jml.partyboi.compos.admin

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.ManualResult
import party.jml.partyboi.compos.NewManualResult
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
        manualResults: List<ManualResult> = emptyList(),
        manualResultForm: Form<NewManualResult>? = null,
    ) = Page(
        title = "Edit compo",
        subLinks = compos.map { it.toNavItem() },
    ) {
        val (qualified, nonQualified) = entries.partition { it.qualified }
        val compo = compoForm.data
        val isManual = compo.manualResults

        h1 { +compo.displayName }

        columns(
            {
                dataForm("/admin/compos/${compo.id}") {
                    article {
                        fieldSet {
                            renderFields(compoForm)
                        }

                        fieldSet {
                            label {
                                input(name = "manualResults") {
                                    type = InputType.checkBox
                                    role = "switch"
                                    checked = compo.manualResults
                                    value = "true"
                                }
                                +"Manual results (admin enters results directly, no submissions or voting)"
                            }
                            label {
                                input(name = "hideAuthor") {
                                    type = InputType.checkBox
                                    role = "switch"
                                    checked = compo.hideAuthor
                                    value = "true"
                                }
                                +"Hide author (author is not shown on voting page or info screen during compo)"
                            }
                        }

                        if (!isManual) {
                            fieldSet {
                                p { +"File uploads" }
                                radioInput(name = "requireFile") {
                                    checked = compo.requireFile == true
                                    value = "true"
                                    label { +"Required" }
                                }
                                radioInput(name = "requireFile") {
                                    checked = compo.requireFile == null
                                    value = "none"
                                    label { +"Optional" }
                                }
                                radioInput(name = "requireFile") {
                                    checked = compo.requireFile == false
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
                                                    .intersect(compo.fileFormats.map { it.name })
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
                                                                compo.fileFormats.any { it.name == format.name }
                                                            +format.description
                                                        }
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
                                        compo.visible,
                                        "Compo visible",
                                        "Compo hidden",
                                        "/admin/compos/${compo.id}/setVisible"
                                    )
                                }
                            }
                            if (!isManual) {
                                tr {
                                    td(classes = "narrow center") { icon("file-arrow-up") }
                                    td {
                                        switchLink(
                                            compo.allowSubmit,
                                            "Submitting open",
                                            "Submitting closed",
                                            "/admin/compos/${compo.id}/setSubmit"
                                        )
                                    }
                                }
                                tr {
                                    td(classes = "narrow center") { icon("check-to-slot") }
                                    td {
                                        switchLink(
                                            compo.allowVote,
                                            "Voting open",
                                            "Voting closed",
                                            "/admin/compos/${compo.id}/setVoting"
                                        )
                                    }
                                }
                            }
                            tr {
                                td(classes = "narrow center") { icon("square-poll-horizontal") }
                                td {
                                    switchLink(
                                        compo.publicResults,
                                        "Results published",
                                        "Results hidden",
                                        "/admin/compos/${compo.id}/publishResults"
                                    )
                                }
                            }
                        }
                    }
                }

            }, if (isManual) {
                {
                    renderManualResultsEditor(
                        compo = compo,
                        manualResults = manualResults,
                        form = manualResultForm ?: Form(
                            NewManualResult::class,
                            NewManualResult.empty(compo.id),
                            initial = true
                        ),
                    )
                }
            } else if (qualified.isNotEmpty() || nonQualified.isNotEmpty()) {
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
                                    attributes.put("data-callback", "/admin/compos/${compo.id}/runOrder")
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
                        a(href = "/admin/compos/${compo.id}/download", classes = "osSpecific") {
                            attributes.put("role", "button")
                            icon("download")
                            +"Download files"
                        }
                    }
                }
            } else null)
        script(src = "/assets/draggable.min.js") {}
    }

    private fun FlowContent.renderManualResultsEditor(
        compo: Compo,
        manualResults: List<ManualResult>,
        form: Form<NewManualResult>,
    ) {
        if (manualResults.isNotEmpty()) {
            val hasTitles = manualResults.any { it.title.isNotBlank() }
            article {
                header { +"Results" }
                table {
                    thead {
                        tr {
                            th(classes = "narrow") {}
                            th(classes = "narrow") { +"Place" }
                            th { +"Author" }
                            if (hasTitles) th { +"Title" }
                            th { +"Score" }
                            th(classes = "settings") {}
                        }
                    }
                    tbody(classes = "sortable") {
                        attributes.put("data-draggable", "tr")
                        attributes.put("data-handle", ".handle")
                        attributes.put("data-callback", "/admin/compos/${compo.id}/manual-results/order")
                        manualResults.forEachIndexed { index, result ->
                            tr {
                                attributes.put("data-dragid", result.id.toString())
                                td(classes = "handle") { icon("arrows-up-down") }
                                td(classes = "place-number") { +"${index + 1}." }
                                td {
                                    a(href = "/admin/compos/${compo.id}/manual-results/${result.id}") {
                                        +result.author
                                    }
                                }
                                if (hasTitles) td { +result.title }
                                td { +result.scoreText }
                                td(classes = "settings") {
                                    deleteButton(
                                        "/admin/compos/${compo.id}/manual-results/${result.id}",
                                        tooltipText = "Delete result",
                                        confirmation = "Delete this result?"
                                    )
                                }
                            }
                        }
                    }
                }
                small {
                    +"Set order by dragging results by "
                    icon("arrows-up-down")
                }
            }
        }

        article {
            header { +"Add result" }
            dataForm("/admin/compos/${compo.id}/manual-results") {
                fieldSet {
                    renderFields(form)
                }
                submitButton("Add result")
            }
        }
    }
}