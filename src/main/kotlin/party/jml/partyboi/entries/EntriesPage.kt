package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.html.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.Entry
import party.jml.partyboi.database.NewEntry
import party.jml.partyboi.database.User
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Renderable

object EntriesPage {
    fun render(app: AppServices, user: User, formData: Form<NewEntry> = Form(NewEntry::class, NewEntry.Empty, initial = true)): Either<AppError, Renderable> = either {
        val userEntries = app.entries.getUserEntries(user.id).bind()
        val compos = app.compos.getAllCompos().bind()
        Page("Submit entries") {
            entryList(userEntries, compos)
            submitNewEntryForm("/entries", compos.filter { it.allowSubmit }, formData)
        }
    }

}

fun FlowContent.entryList(userEntries: List<Entry>, compos: List<Compo>) {
    article {
        header { +"My entries" }
        if (userEntries.isEmpty()) {
            p { +"Nothing yet, please upload something soon!" }
        } else {
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
