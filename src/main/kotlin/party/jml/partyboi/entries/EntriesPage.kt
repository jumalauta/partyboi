package party.jml.partyboi.entries

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.renderReadonlyFields
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonGroup
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.entryCard
import party.jml.partyboi.templates.components.icon

object EntriesPage {
    fun render(
        newEntryForm: Form<NewEntry>,
        compos: List<Compo>,
        userEntries: List<EntryWithLatestFile>,
        previews: List<Preview>,
    ) = Page("Submit entries") {
        h1 { +"Entries" }

        if (compos.isEmpty() && userEntries.isEmpty()) {
            article { +"Submitting entries disabled. \uD83D\uDE25" }
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
        h1 { +"My entries" }

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
                        a {
                            href = "#"
                            role = "button"
                            onClick = Javascript.build {
                                confirm("Do you really want to delete entry \"${entry.title}\" by ${entry.author}?") {
                                    httpDelete("/entries/${entry.id}")
                                    refresh()
                                }
                            }
                            icon("trash")
                            +" Delete"
                        }
                    }
                }
            }
        }
    }
}

fun FlowContent.submitNewEntryForm(url: String, openCompos: List<Compo>, values: Form<NewEntry>) {
    if (openCompos.isEmpty()) {
        article { +"Submitting is closed" }
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
