package party.jml.partyboi.partytemplate.admin

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.partytemplate.ImportPreview
import party.jml.partyboi.partytemplate.ImportReport
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.system.displayDateTime
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonLink
import party.jml.partyboi.templates.components.cardHeader

object PartyTemplatePages {
    fun renderExportPage(
        generalRules: String,
        compos: List<Compo>,
        events: List<Event>,
        timeZone: TimeZone,
    ): Page = Page("Export party template") {
        h1 { +"Export party template" }

        dataForm("/admin/settings/export") {
            // The response is a file download, not a navigation, so the submit
            // progress bar would never clear.
            attributes["data-no-progress"] = "true"
            article {
                cardHeader("What to export")
                p {
                    +"The template is a JSON file with the selected compos, general compo rules and schedule. "
                    +"Schedule times are stored relative to the party start date, so they land on the right days "
                    +"when imported into next year's Partyboi."
                }
                p {
                    small { +"Schedule triggers that point to a compo left out of the export are not included." }
                }

                fieldSet {
                    legend { +"General rules" }
                    label {
                        input(name = "generalRules") {
                            type = InputType.checkBox
                            checked = generalRules.isNotBlank()
                        }
                        // Pico turns a small adjacent to an input into a full-width
                        // helper-text block; wrapping the label text in a span keeps
                        // the smalls inline.
                        span {
                            +" General compo rules"
                            if (generalRules.isBlank()) {
                                small { +" (empty)" }
                            }
                        }
                    }
                }

                fieldSet {
                    legend { +"Compos" }
                    if (compos.isEmpty()) p { small { +"No compos" } }
                    compos.forEach { compo ->
                        label {
                            input(name = "compoIds") {
                                type = InputType.checkBox
                                value = compo.id.toString()
                                checked = true
                            }
                            +" ${compo.name}"
                        }
                    }
                }

                fieldSet {
                    legend { +"Schedule" }
                    if (events.isEmpty()) p { small { +"No events" } }
                    events.forEach { event ->
                        label {
                            input(name = "eventIds") {
                                type = InputType.checkBox
                                value = event.id.toString()
                                checked = true
                            }
                            span {
                                +" ${event.name} "
                                small { +"— ${event.startTime.displayDateTime(timeZone)}" }
                            }
                        }
                    }
                }

                submitButton("Export template")
            }
        }
    }

    fun renderImportSelectionPage(
        preview: ImportPreview,
        payload: String,
        confirmUrl: String,
        timeZone: TimeZone,
    ): Page = Page("Import party template") {
        h1 { +"Import party template" }

        if (preview.hasNothing) {
            article {
                cardHeader("Nothing to import")
                p { +"The template file does not contain any compos, events or general rules." }
            }
            return@Page
        }

        dataForm(confirmUrl) {
            article {
                cardHeader("What to import")
                p { +"Select the items to import. Items with a name that already exists are skipped." }

                hiddenInput(name = "payload") { value = payload }

                if (preview.hasGeneralRules) {
                    fieldSet {
                        legend { +"General rules" }
                        label {
                            input(name = "generalRules") {
                                type = InputType.checkBox
                                checked = !preview.generalRulesOverwrite
                            }
                            // Spans keep the smalls inline; a small adjacent to an
                            // input is styled as a full-width helper-text block by Pico.
                            span {
                                +" General compo rules"
                                if (preview.generalRulesOverwrite) {
                                    small { +" — overwrites the current general rules" }
                                }
                            }
                        }
                    }
                }

                if (preview.compos.isNotEmpty()) {
                    fieldSet {
                        legend { +"Compos" }
                        preview.compos.forEach { compo ->
                            label {
                                input(name = "compos") {
                                    type = InputType.checkBox
                                    value = compo.index.toString()
                                    checked = !compo.alreadyExists
                                    disabled = compo.alreadyExists
                                }
                                span {
                                    +" ${compo.name}"
                                    if (compo.alreadyExists) {
                                        small { +" — already exists, skipped" }
                                    }
                                }
                            }
                        }
                    }
                }

                if (preview.events.isNotEmpty()) {
                    fieldSet {
                        legend { +"Schedule" }
                        preview.events.forEach { event ->
                            label {
                                input(name = "events") {
                                    type = InputType.checkBox
                                    value = event.index.toString()
                                    checked = !event.alreadyExists
                                    disabled = event.alreadyExists
                                }
                                span {
                                    +" ${event.name} "
                                    small { +"— ${event.startTime.displayDateTime(timeZone)}" }
                                    if (event.alreadyExists) {
                                        small { +" — already exists, skipped" }
                                    }
                                }
                            }
                            event.triggerLabels.forEach { trigger ->
                                p(classes = "template-trigger") {
                                    small { +"↳ $trigger" }
                                }
                            }
                        }
                        p {
                            small { +"Triggers whose compo is not imported and does not already exist are left out." }
                        }
                    }
                }

                submitButton("Import selected")
            }
        }
    }

    fun renderImportReportPage(report: ImportReport, continueUrl: String): Page =
        Page("Party template imported") {
            h1 { +"Party template imported" }

            article {
                cardHeader("Import results")
                ul {
                    if (report.generalRulesImported) li { +"General compo rules imported" }
                    if (report.createdCompos.isNotEmpty()) li { +"Compos created: ${report.createdCompos.joinToString()}" }
                    if (report.skippedCompos.isNotEmpty()) li { +"Compos skipped (already exist): ${report.skippedCompos.joinToString()}" }
                    if (report.createdEvents.isNotEmpty()) li { +"Events created: ${report.createdEvents.joinToString()}" }
                    if (report.skippedEvents.isNotEmpty()) li { +"Events skipped (already exist): ${report.skippedEvents.joinToString()}" }
                    if (report.createdTriggers > 0) li { +"Schedule triggers created: ${report.createdTriggers}" }
                    if (report.droppedTriggers.isNotEmpty()) li { +"Triggers left out: ${report.droppedTriggers.joinToString()}" }
                    if (!report.generalRulesImported &&
                        report.createdCompos.isEmpty() && report.skippedCompos.isEmpty() &&
                        report.createdEvents.isEmpty() && report.skippedEvents.isEmpty()
                    ) {
                        li { +"Nothing was selected for import" }
                    }
                }
                footer {
                    buttonLink(continueUrl) { +"Continue" }
                }
            }
        }
}
