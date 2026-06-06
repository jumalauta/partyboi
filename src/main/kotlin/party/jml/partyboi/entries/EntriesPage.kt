package party.jml.partyboi.entries

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.renderReadonlyFields
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*

object EntriesPage {
    fun render(
        newEntryForm: Form<NewEntry>,
        compos: List<Compo>,
        userEntries: List<EntryWithLatestFile>,
        previews: List<Preview>,
    ) = Page("Entries") {
        h1 { +"Entries" }

        if (compos.isEmpty() && userEntries.isEmpty()) {
            article { +"Submitting entries is disabled." }
        }

        columns(
            if (compos.isNotEmpty()) {
                { submitNewEntryForm("/entries", compos, newEntryForm) }
            } else null,
            if (userEntries.isNotEmpty()) {
                { entryList(userEntries, compos, previews) }
            } else null
        )
    }
}

fun FlowContent.entryList(
    userEntries: List<EntryWithLatestFile>,
    compos: List<Compo>,
    previews: List<Preview>,
) {
    if (userEntries.isNotEmpty()) {
        h2 { +"My entries" }

        userEntries.forEach { entry ->
            entryCard(entry, previews.find { it.entryId == entry.id }, compos) {
                val compo = compos.find { it.id == entry.compoId }
                val allowSubmit = compo?.allowSubmit == true

                buttonGroup {
                    a {
                        href = "/entries/${entry.id}"
                        role = "button"
                        if (allowSubmit) {
                            icon("pen-to-square")
                            +" Edit"
                        } else {
                            icon("eye")
                            +" View"
                        }
                    }

                    if (compo?.allowSubmit == true) {
                        deleteButton(
                            url = "/entries/${entry.id}",
                            tooltipText = "Delete entry",
                            confirmation = confirmDelete("entry", entry.title),
                        )
                    }
                }
            }
        }
    }
}

fun FlowContent.submitNewEntryForm(url: String, openCompos: List<Compo>, values: Form<NewEntry>) {
    if (openCompos.isEmpty()) {
        article { +"Submitting is closed." }
    } else {
        renderForm(
            title = "Submit a new entry",
            url = url,
            form = values,
            options = mapOf("compoId" to openCompos.map { it.toDropdownOption() }),
            submitButtonLabel = "Submit"
        )
    }
}

fun FlowContent.entryDetails(compos: List<Compo>, values: Form<EntryUpdate>) {
    article {
        renderReadonlyFields(values, mapOf("compoId" to compos))
    }
}
