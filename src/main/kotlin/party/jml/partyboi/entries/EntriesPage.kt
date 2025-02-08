package party.jml.partyboi.entries

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.form.renderReadonlyFields
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*

object EntriesPage {
    fun render(
        newEntryForm: Form<NewEntry>,
        compos: List<Compo>,
        userEntries: List<EntryWithLatestFile>,
        screenshots: List<Screenshot>,
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
                { entryList(userEntries, compos, screenshots) }
            } else null
        )
    }
}

fun FlowContent.entryList(
    userEntries: List<EntryWithLatestFile>,
    compos: List<Compo>,
    screenshots: List<Screenshot>,
) {
    if (userEntries.isNotEmpty()) {
        h1 { +"My entries" }

        userEntries.forEach { entry ->
            entryCard(entry, screenshots.find { it.entryId == entry.id }, compos) {
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
    dataForm(url) {
        article {
            if (openCompos.isEmpty()) {
                +"Submitting is closed"
            } else {
                cardHeader("Submit a new entry")
                fieldSet {
                    renderFields(
                        values, mapOf(
                            "compoId" to openCompos.map { it.toDropdownOption() }
                        ))
                }
                footer {
                    submitInput { value = "Submit" }
                }
            }
        }
    }
}

fun FlowContent.editEntryForm(url: String, compos: List<Compo>, values: Form<EntryUpdate>) {
    dataForm(url) {
        article {
            fieldSet {
                renderFields(values, mapOf("compoId" to compos))
            }
            footer {
                submitInput { value = "Save changes" }
            }
        }
    }
}

fun FlowContent.entryDetails(compos: List<Compo>, values: Form<EntryUpdate>) {
    article {
        renderReadonlyFields(values, mapOf("compoId" to compos))
    }
}
