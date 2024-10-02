@file:Suppress("EscapedRaise")

package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.database.*
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.catchError
import party.jml.partyboi.form.Form
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.templates.*

fun Application.configureEntriesRouting(app: AppServices) {
    fun entriesPage(user: User, formData: Form<NewEntry> = Form(NewEntry::class, NewEntry.Empty, initial = true)): Either<AppError, Renderable> = either {
        val userEntries = app.entries.getUserEntries(user.id).bind()
        val compos = app.compos.getAllCompos().bind()
        Page("Submit entries") {
            entryList(userEntries, compos)
            submitNewEntryForm("/entries", compos.filter { it.allowSubmit }, formData)
        }
    }

    fun editEntryPage(formData: Form<EntryUpdate>): Either<AppError, Renderable> = either {
        val compos = app.compos.getAllCompos().bind()
        Page("Edit entry") {
            editEntryForm("/entries/${formData.data.id}", compos.filter { it.allowSubmit }, formData)
        }
    }


    routing {
        authenticate("user") {
            get("/entries/{id}") {
                call.respondEither { either {
                    val id = catchError { call.parameters["id"]?.toInt() ?: -1 }.bind()
                    val user = call.userSession().bind()
                    val entry = app.entries.get(id, user.id).bind()
                    val form = Form(EntryUpdate::class, EntryUpdate.fromEntry(entry), initial = true)

                    editEntryPage(form).bind()
                } }
            }

            get("/entries") {
                call.respondEither {
                    call.userSession().flatMap { entriesPage(it) }
                }
            }

            post("/entries") {
                val user = call.userSession()
                val submitRequest = Form.fromParameters<NewEntry>(call.receiveMultipart())

                call.respondEither({ _ -> either { entriesPage(user.bind(), submitRequest.bind()) }.flatten() }) {
                    either {
                        val userId = user.bind().id
                        val form = submitRequest.bind()
                        val newEntry = form.validated().bind().copy(userId = userId)
                        runBlocking { newEntry.file.writeTo("/Users/ilkkahanninen/dev/temp/compos").bind() }
                        app.entries.add(newEntry).bind()
                        RedirectPage("/entries")
                    }
                }
            }

            post("/entries/{id}") {
                val maybeUser = call.userSession()
                val submitRequest = Form.fromParameters<EntryUpdate>(call.receiveMultipart())

                call.respondEither({ _ -> submitRequest.flatMap { editEntryPage(it) } }) {
                    either {
                        val userId = maybeUser.bind().id
                        val form = submitRequest.bind()
                        val entry = form.validated().bind()
                        app.entries.update(entry, userId).bind()
                        if (entry.file.isDefined) {
                            runBlocking { entry.file.writeTo("/Users/ilkkahanninen/dev/temp/compos").bind() }
                        }
                        RedirectPage("/entries")
                    }
                }
            }

        }

        authenticate("user", optional = true) {
            delete("/entries/{id}") {
                either {
                    val id = catchError { call.parameters["id"]?.toInt() ?: -1 }.bind()
                    val user = call.userSession().bind()
                    app.entries.delete(id, user.id).bind()
                }.mapLeft {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
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
