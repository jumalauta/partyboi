package party.jml.partyboi.entries

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.submitNewEntryForm
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.columns

object EntriesPage {
    fun render(
        newEntryForm: Form<NewEntry>,
        compos: List<Compo>,
        userEntries: List<EntryWithLatestFile>,
    ) =
        Page("Submit entries") {
            h1 { +"Entries" }

            if (compos.isEmpty() && userEntries.isEmpty()) {
                article { +"You haven't submitted any entries and all the compos have already been closed. \uD83D\uDE25" }
            }

            columns(
                if (compos.isNotEmpty()) {
                    { submitNewEntryForm("/entries", compos, newEntryForm) }
                } else null,
                if (userEntries.isNotEmpty()) {
                    { entryList(userEntries, compos) }
                } else null
            )
        }
}

fun FlowContent.entryList(userEntries: List<EntryWithLatestFile>, compos: List<Compo>) {
    if (userEntries.isNotEmpty()) {
        article {
            header { +"My entries" }
            table {
                thead {
                    tr {
                        th { +"Title" }
                        th { +"Author" }
                        th { +"Compo" }
                        th {}
                    }
                }
                tbody {
                    userEntries.map { entry ->
                        val compo = compos.find { it.id == entry.compoId }
                        tr {
                            td {
                                a(href = "/entries/${entry.id}") {
                                    +entry.title
                                }
                            }
                            td { +entry.author }
                            td { +(compo?.name ?: "Invalid compo") }
                            td {
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
                                        +"Delete"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
