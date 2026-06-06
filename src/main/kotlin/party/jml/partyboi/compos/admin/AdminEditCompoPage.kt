package party.jml.partyboi.compos.admin

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.ManualResult
import party.jml.partyboi.compos.NewManualResult
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.entries.FileFormatCategory
import party.jml.partyboi.entries.Preview
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import java.util.*
import kotlin.math.roundToInt

/** The two tabs of the compo edit page; each is its own route. */
enum class CompoTab { SETTINGS, ENTRIES }

object AdminEditCompoPage {
    fun render(
        compoForm: Form<Compo>,
        entries: List<Entry>,
        compos: List<Compo>,
        files: Map<UUID, FileDesc> = emptyMap(),
        previews: Map<UUID, Preview> = emptyMap(),
        manualResults: List<ManualResult> = emptyList(),
        manualResultForm: Form<NewManualResult>? = null,
        editManualResultId: String? = null,
        activeTab: CompoTab = CompoTab.SETTINGS,
    ) = Page(
        title = "Edit compo",
        subLinks = compos.map { it.toNavItem() },
    ) {
        val compo = compoForm.data
        val isManual = compo.manualResults

        val qualified = entries.filter { it.qualified }
        val qualifiedOrder = qualified.mapIndexed { i, e -> e.id to (i + 1) }.toMap()

        // Runtime estimate over qualified entries only (see design_handoff_compo_edit/README.md).
        val content = qualified.sumOf { effectiveDuration(it, compo.defaultSlotSec) }
        val buffer = maxOf(0, qualified.size - 1) * compo.changeoverSec
        val total = content + buffer
        val needsDur = qualified.count { it.duration == null }

        val secondTabLabel = if (isManual) "Results" else "Entries"
        val secondTabCount = if (isManual) manualResults.size else qualified.size
        val secondTabHref =
            if (isManual) "/admin/compos/${compo.id}/results" else "/admin/compos/${compo.id}/entries"

        div(classes = "compo-edit") {
            h1 { +compo.displayName }

            reloadSection {
                tabBar(
                    listOf(
                        Tab("Settings", "/admin/compos/${compo.id}", active = activeTab == CompoTab.SETTINGS),
                        Tab("$secondTabLabel ($secondTabCount)", secondTabHref, active = activeTab == CompoTab.ENTRIES),
                    )
                )

                when (activeTab) {
                    CompoTab.SETTINGS -> renderSettingsTab(compoForm, isManual)
                    CompoTab.ENTRIES ->
                        if (isManual) {
                            renderManualResultsEditor(
                                compo = compo,
                                manualResults = manualResults,
                                form = manualResultForm ?: Form(
                                    NewManualResult::class,
                                    NewManualResult.empty(compo.id),
                                    initial = true
                                ),
                                editResultId = editManualResultId,
                            )
                        } else {
                            renderEntriesTab(
                                compo = compo,
                                entries = entries,
                                files = files,
                                previews = previews,
                                qualifiedOrder = qualifiedOrder,
                                qualifiedCount = qualified.size,
                                content = content,
                                buffer = buffer,
                                total = total,
                                needsDur = needsDur,
                            )
                        }
                }
            }
        }
        script(src = "/assets/draggable.min.js") {}
    }

    // ---------------------------------------------------------------- Settings tab

    private fun FlowContent.renderSettingsTab(compoForm: Form<Compo>, isManual: Boolean) {
        val compo = compoForm.data
        columns({
            dataForm("/admin/compos/${compo.id}") {
                article {
                    header { +"General" }
                    fieldSet { renderFields(compoForm) }
                }

                article {
                    header { +"Visibility & results" }
                    p { small { +"How this compo behaves during the party." } }
                    fieldSet {
                        picoSwitch(
                            name = "manualResults",
                            checked = compo.manualResults,
                            label = "Manual results",
                            help = "Admin enters results directly — no submissions or voting.",
                        )
                        picoSwitch(
                            name = "hideAuthor",
                            checked = compo.hideAuthor,
                            label = "Hide author",
                            help = "Author isn't shown on the voting page or info screen during the compo.",
                        )
                    }
                }

                if (!isManual) {
                    article {
                        header { +"Submissions" }

                        fieldSet {
                            legend { +"File uploads" }
                            picoRadio("requireFile", "true", "Required", compo.requireFile == true)
                            picoRadio("requireFile", "none", "Optional", compo.requireFile == null)
                            picoRadio("requireFile", "false", "Disabled", compo.requireFile == false)
                        }

                        fieldSet {
                            legend { +"Accepted file formats" }
                            p { small { +"Open a category to choose extensions." } }
                            FileFormatCategory.entries.forEach { cat ->
                                val formats = cat.formats()
                                val anySelected = formats.any { f -> compo.fileFormats.any { it.name == f.name } }
                                details {
                                    if (anySelected) attributes["open"] = ""
                                    summary { +cat.description }
                                    formats.forEach { format ->
                                        label {
                                            input(name = "fileFormats") {
                                                type = InputType.checkBox
                                                value = format.name
                                                checked = compo.fileFormats.any { it.name == format.name }
                                            }
                                            +(" " + format.description)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    article {
                        header { +"Timing" }
                        p { small { +"Feeds the runtime estimate on the Entries tab." } }
                        div(classes = "grid") {
                            label {
                                +"Changeover between entries (seconds)"
                                numberInput(name = "changeoverSec") {
                                    value = compo.changeoverSec.toString()
                                    min = "0"
                                }
                                small { +"Buffer added between each run." }
                            }
                            label {
                                +"Default slot for stills / no media (seconds)"
                                numberInput(name = "defaultSlotSec") {
                                    value = compo.defaultSlotSec.toString()
                                    min = "0"
                                }
                                small { +"Used when no media length is detected (intros, graphics)." }
                            }
                        }
                        fieldSet {
                            legend { +"Duration source" }
                            label {
                                radioInput { checked = true }
                                +" Auto-detect from media"
                            }
                            small { +"Override any entry's duration on the Entries tab." }
                        }
                    }
                }

                buttonGroup {
                    button(type = ButtonType.submit) { +"Save changes" }
                }
            }
        }, {

            // Live state controls live outside the settings form: toggling them PUTs and
            // reloads immediately, independent of the unsaved form fields above.
            article {
                header { +"State" }
                table {
                    tbody {
                        tr {
                            td(classes = "narrow center") { icon("eye") }
                            td {
                                switchLink(
                                    compo.visible, "Compo visible", "Compo hidden",
                                    "/admin/compos/${compo.id}/setVisible"
                                )
                            }
                        }
                        if (!isManual) {
                            tr {
                                td(classes = "narrow center") { icon("file-arrow-up") }
                                td {
                                    switchLink(
                                        compo.allowSubmit, "Submitting open", "Submitting closed",
                                        "/admin/compos/${compo.id}/setSubmit"
                                    )
                                }
                            }
                            tr {
                                td(classes = "narrow center") { icon("check-to-slot") }
                                td {
                                    switchLink(
                                        compo.allowVote, "Voting open", "Voting closed",
                                        "/admin/compos/${compo.id}/setVoting"
                                    )
                                }
                            }
                        }
                        tr {
                            td(classes = "narrow center") { icon("square-poll-horizontal") }
                            td {
                                switchLink(
                                    compo.publicResults, "Results published", "Results hidden",
                                    "/admin/compos/${compo.id}/publishResults"
                                )
                            }
                        }
                    }
                }
            }
        })
    }

    // ---------------------------------------------------------------- Entries tab

    private fun FlowContent.renderEntriesTab(
        compo: Compo,
        entries: List<Entry>,
        files: Map<UUID, FileDesc>,
        previews: Map<UUID, Preview>,
        qualifiedOrder: Map<UUID, Int>,
        qualifiedCount: Int,
        content: Int,
        buffer: Int,
        total: Int,
        needsDur: Int,
    ) {
        // Runtime summary
        article {
            div(classes = "grid pb-summary") {
                pbStat(qualifiedCount.toString(), "Qualified")
                pbStat(fmtDur(total), "Est. runtime")
                pbStat(fmtDur(content), "Content")
                pbStat("+" + fmtDur(buffer), "Changeover")
            }
        }

        // Toolbar
        div(classes = "title-row") {
            searchInput(classes = "pb-filter") {
                attributes["placeholder"] = "Filter entries…"
                attributes["aria-label"] = "Filter entries"
            }
            div(classes = "title-row-actions") {
                a(href = "/entries/submit/${compo.id}", classes = "secondary outline") {
                    attributes["role"] = "button"
                    icon("plus")
                    +" Add entry"
                }
                a(href = "/admin/compos/${compo.id}/download", classes = "osSpecific") {
                    attributes["role"] = "button"
                    icon("download")
                    +" Download files"
                }
            }
        }

        // Entries table
        article {
            if (entries.isEmpty()) {
                p { +"No entries yet." }
            } else {
                table {
                    thead {
                        tr {
                            th(classes = "narrow") {}
                            th(classes = "narrow") { +"#" }
                            th(classes = "narrow") { +"Prev" }
                            th { +"Title" }
                            th { +"Duration" }
                            th { +"File" }
                            th(classes = "narrow center") { +"Q" }
                            th(classes = "settings") {}
                        }
                    }
                    tbody(classes = "sortable") {
                        attributes["data-draggable"] = "tr"
                        attributes["data-handle"] = ".handle"
                        attributes["data-callback"] = "/admin/compos/${compo.id}/runOrder"
                        attributes["data-reload-on-sort"] = "true"
                        entries.forEach { entry ->
                            renderEntryRow(
                                entry = entry,
                                file = files[entry.id],
                                preview = previews[entry.id],
                                order = qualifiedOrder[entry.id],
                            )
                        }
                    }
                }
                small {
                    icon("grip-vertical")
                    +" Drag to set run order · durations auto-detected from uploaded media"
                }
            }
        }
    }

    private fun TBODY.renderEntryRow(
        entry: Entry,
        file: FileDesc?,
        preview: Preview?,
        order: Int?,
    ) {
        tr(classes = if (entry.qualified) null else "not-qualified") {
            attributes["data-dragid"] = entry.id.toString()
            attributes["data-search"] = "${entry.title} ${entry.author}".lowercase()

            td(classes = "handle") { icon("grip-vertical") }
            td(classes = "narrow") { small { +(order?.toString()?.padStart(2, '0') ?: "–") } }
            td(classes = "narrow") { renderThumbnail(entry, preview) }
            td {
                a(href = "/entries/${entry.id}") { +entry.title }
                br {}
                small { +entry.author }
            }
            td {
                numberInputDuration(entry)
            }
            td {
                if (file != null) {
                    code { +"${file.extension.uppercase()} · ${humanSize(file.size)}" }
                } else {
                    mark { +"missing" }
                }
            }
            td(classes = "narrow center") {
                toggleButton(
                    entry.qualified,
                    IconSet.qualified,
                    "/admin/compos/entries/${entry.id}/setQualified"
                )
            }
            td(classes = "settings") {
                a(href = "/entries/${entry.id}", classes = "flat-button") {
                    icon("pen-to-square", "Edit entry")
                }
                if (file != null) {
                    a(href = "/entries/download/${file.id}", classes = "flat-button") {
                        icon("download", "Download file")
                    }
                }
                toggleButton(
                    entry.allowEdit,
                    IconSet.allowEdit,
                    "/admin/compos/entries/${entry.id}/allowEdit"
                )
                deleteButton(
                    url = "/admin/compos/entries/${entry.id}",
                    tooltipText = "Delete entry",
                    confirmation = confirmDelete("entry", entry.title),
                )
            }
        }
    }

    private fun TD.numberInputDuration(entry: Entry) {
        textInput(classes = "pb-dur-input") {
            attributes["data-entry-id"] = entry.id.toString()
            attributes["aria-label"] = "Duration (m:ss)"
            attributes["placeholder"] = "m:ss"
            value = entry.duration?.let { fmtDur(it.roundToInt()) } ?: ""
        }
    }

    private fun FlowContent.renderThumbnail(entry: Entry, preview: Preview?) {
        if (preview == null) {
            span(classes = "pb-thumb pb-thumb-empty") { icon("image") }
        } else {
            val type = if (preview.previewFileIsVideo) "video" else "image"
            val hasAudio = preview.previewAudioFilePath != null
            span(classes = "pb-thumb clickable-preview" + if (hasAudio) " has-audio" else "") {
                attributes["tabindex"] = "0"
                attributes["role"] = "button"
                attributes["data-preview-url"] = preview.externalPreviewFileUrl()
                attributes["data-preview-type"] = type
                if (hasAudio) {
                    attributes["data-preview-audio-url"] = preview.externalPreviewAudioFileUrl()
                }
                img(src = preview.externalUrl(), alt = entry.title)
                if (hasAudio) span(classes = "play-overlay") { icon("play") }
            }
        }
    }

    // ---------------------------------------------------------------- Small helpers

    private fun FlowContent.picoSwitch(name: String, checked: Boolean, label: String, help: String) {
        label {
            input(name = name) {
                type = InputType.checkBox
                role = "switch"
                value = "true"
                this.checked = checked
            }
            +(" $label")
        }
        small { +help }
    }

    private fun FlowContent.picoRadio(name: String, value: String, label: String, checked: Boolean) {
        label {
            input(name = name) {
                type = InputType.radio
                this.value = value
                this.checked = checked
            }
            +(" $label")
        }
    }

    private fun FlowContent.pbStat(value: String, label: String) {
        div(classes = "pb-stat") {
            strong { +value }
            small { +label }
        }
    }

    private fun fmtDur(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    private fun effectiveDuration(entry: Entry, defaultSlotSec: Int): Int =
        entry.duration?.roundToInt() ?: defaultSlotSec

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var idx = 0
        while (value >= 1024 && idx < units.lastIndex) {
            value /= 1024
            idx++
        }
        val rounded = if (value >= 10) value.roundToInt().toString()
        else ((value * 10).roundToInt() / 10.0).toString()
        return "$rounded ${units[idx]}"
    }

    // ---------------------------------------------------------------- Manual results

    private fun FlowContent.renderManualResultsEditor(
        compo: Compo,
        manualResults: List<ManualResult>,
        form: Form<NewManualResult>,
        editResultId: String? = null,
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
                                td(classes = "handle") { icon("grip-vertical") }
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
                                        confirmation = confirmDelete("result", result.author)
                                    )
                                }
                            }
                        }
                    }
                }
                small {
                    +"Set order by dragging results by "
                    icon("grip-vertical")
                }
            }
        }

        article {
            if (editResultId != null) {
                header { +"Edit result" }
                dataForm("/admin/compos/${compo.id}/manual-results/$editResultId") {
                    fieldSet {
                        renderFields(form)
                    }
                    footer {
                        submitInput { value = "Save changes" }
                        a(href = "/admin/compos/${compo.id}", classes = "secondary") {
                            attributes["role"] = "button"
                            +"Cancel"
                        }
                    }
                }
            } else {
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
}
