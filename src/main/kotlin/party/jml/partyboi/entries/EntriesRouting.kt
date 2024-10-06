@file:Suppress("EscapedRaise")

package party.jml.partyboi.entries

import arrow.core.flatten
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.database.EntryUpdate
import party.jml.partyboi.database.NewEntry
import party.jml.partyboi.database.NewFileDesc
import party.jml.partyboi.errors.catchError
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither

fun Application.configureEntriesRouting(app: AppServices) {
    routing {
        authenticate("user") {
            get("/entries") {
                call.respondEither({ either {
                    val user = call.userSession().bind()
                    val form = Form(NewEntry::class, NewEntry.Empty, initial = true)
                    val compos = app.compos.getAllCompos().bind().filter { it.canSubmit(user) }
                    val userEntries = app.entries.getUserEntries(user.id).bind()
                    EntriesPage.render(compos, userEntries, form)
                }})
            }

            get("/entries/submit/{compoId}") {
                call.respondEither({ either {
                    val compoId = call.parameterInt("compoId").bind()
                    val user = call.userSession().bind()
                    val form = Form(NewEntry::class, NewEntry.Empty.copy(compoId = compoId), initial = true)
                    val compos = app.compos.getAllCompos().bind().filter { it.canSubmit(user) }
                    val userEntries = app.entries.getUserEntries(user.id).bind()
                    EntriesPage.render(compos, userEntries, form)
                }})
            }

            post("/entries") {
                val user = call.userSession()
                val submitRequest = Form.fromParameters<NewEntry>(call.receiveMultipart())

                call.respondEither({ either {
                    val userId = user.bind().id
                    val form = submitRequest.bind()
                    val newEntry = form.validated().bind().copy(userId = userId)

                    app.compos.assertCanSubmit(newEntry.compoId, user.bind().isAdmin).bind()
                    app.entries.add(newEntry).bind()

                    RedirectPage("/entries")
                } }, { error -> either {
                    val compos = app.compos.getAllCompos().bind().filter { it.canSubmit(user.bind()) }
                    val userEntries = app.entries.getUserEntries(user.bind().id).bind()
                    EntriesPage.render(compos, userEntries, submitRequest.bind().with(error))
                } })
            }

            get("/entries/{id}") {
                call.respondEither({ either {
                    val id = catchError { call.parameters["id"]?.toInt() ?: -1 }.bind()
                    val user = call.userSession().bind()
                    val entry = app.entries.get(id, user.id).bind()
                    val form = Form(EntryUpdate::class, EntryUpdate.fromEntry(entry), initial = true)

                    EditEntryPage.render(app, form).bind()
                } })
            }

            post("/entries/{id}") {
                val maybeUser = call.userSession()
                val submitRequest = Form.fromParameters<EntryUpdate>(call.receiveMultipart())

                call.respondEither({ either {
                    val userId = maybeUser.bind().id
                    val form = submitRequest.bind()
                    val entry = form.validated().bind()
                    app.entries.update(entry, userId).bind()
                    if (entry.file.isDefined) {
                        runBlocking { entry.file.write(Config.getEntryDir()).bind() }
                    }
                    RedirectPage("/entries")
                } }, { error -> either {
                    EditEntryPage.render(app, submitRequest.bind().with(error))
                }.flatten() })
            }
        }

        // API routes (we don't want to redirect user to login page)
        authenticate("user", optional = true) {
            delete("/entries/{id}") {
                call.apiRespond { either {
                    val user = call.userSession().bind()
                    val id = call.parameterInt("id").bind()
                    app.entries.delete(id, user.id).bind()
                } }
            }
        }
    }
}
