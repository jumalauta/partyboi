package party.jml.partyboi.entries

import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.Entry
import party.jml.partyboi.database.NewEntry
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page

object EntriesPage {
    fun render(compos: List<Compo>, userEntries: List<Entry>, formData: Form<NewEntry>) =
        Page("Submit entries") {
            submitNewEntryForm("/entries", compos, formData)
            entryList(userEntries, compos)
        }
}

fun FlowContent.entryList(userEntries: List<Entry>, compos: List<Compo>) {
    if (userEntries.isNotEmpty()) {
        article {
            header { +"My entries" }
            table {
                thead {
                    tr {
                        th { +"Title" }
                        th { +"Author" }
                        th { +"Compo" }
                        th { +"File" }
                        th {}
                    }
                }
                tbody {
                    userEntries.map { entry ->
                        val compo = compos.find { it.id == entry.compoId }
                        tr {
                            td {
                                a(href="/entries/${entry.id}") {
                                    +entry.title
                                }
                            }
                            td { +entry.author }
                            td { +(compo?.name ?: "Invalid compo") }
                            td { +entry.filename }
                            td {
                                if (compo?.allowSubmit == true) {
                                    a {
                                        href="#"
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
