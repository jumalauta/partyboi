package party.jml.partyboi.entries

import arrow.core.getOrElse
import kotlinx.html.*
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.Entry
import party.jml.partyboi.database.EntryWithLatestFile
import party.jml.partyboi.database.NewEntry
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page

object EntriesPage {
    fun render(compos: List<Compo>, userEntries: List<EntryWithLatestFile>, formData: Form<NewEntry>) =
        Page("Submit entries") {
            submitNewEntryForm("/entries", compos, formData)
            entryList(userEntries, compos)
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
                            td {
                                entry.originalFilename
                                    .map { +it }
                                    .getOrElse { span(classes = "error") { +"File not uploaded" } }
                                +entry.fileSize.map { " (${Filesize.humanFriendly(it)})" }.getOrElse{ "" }
                            }
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
