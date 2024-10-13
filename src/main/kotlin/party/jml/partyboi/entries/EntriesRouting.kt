@file:Suppress("EscapedRaise")

package party.jml.partyboi.entries

import arrow.core.flatten
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.NotFound
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.catchError
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

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
                    val id = call.parameterInt("id").bind()
                    val user = call.userSession().bind()
                    val entry = app.entries.get(id, user.id).bind()
                    val form = Form(EntryUpdate::class, EntryUpdate.fromEntry(entry), initial = true)
                    val files = app.files.getAllVersions(id).bind()
                    val screenshot = app.screenshots.get(id).map { "/entries/$id/screenshot.jpg" }

                    EditEntryPage.render(app, form, files, screenshot).bind()
                } })
            }

            get("/entries/{id}/screenshot.jpg") {
                either {
                    val id = call.parameterInt("id").bind()
                    app.screenshots.get(id)
                        .toEither { NotFound("Entry screenshot not found") }
                        .bind()
                }.fold(
                    { call.respondPage(it) },
                    { call.respondFile(it.toFile()) }
                )
            }

            get("/entries/{id}/download/{version}") {
                either {
                    val id = call.parameterInt("id").bind()
                    val version = call.parameterInt("version").bind()
                    val user = call.userSession().bind()
                    app.files.getUserVersion(id, version, user.id).bind()
                }.fold(
                    { call.respondPage(it) },
                    {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName,
                                it.originalFilename
                            ).toString()
                        )
                        call.respondFile(it.getStorageFile())
                    }
                )
            }

            post("/entries/{id}") {
                val maybeUser = call.userSession()
                val submitRequest = Form.fromParameters<EntryUpdate>(call.receiveMultipart())

                call.respondEither({ either {
                    val user = maybeUser.bind()
                    val form = submitRequest.bind()
                    val entry = form.validated().bind()

                    app.compos.assertCanSubmit(entry.compoId, user.isAdmin).bind()

                    val newEntry = app.entries.update(entry, user.id).bind()
                    if (entry.file.isDefined) {
                        val storageFilename = app.files.makeStorageFilename(newEntry, entry.file.name).bind()
                        runBlocking { entry.file.write(storageFilename).bind() }
                        val file = app.files.add(NewFileDesc(entry.id, entry.file.name, storageFilename)).bind()

                        app.screenshots.scanForScreenshotSource(file).map { source ->
                            app.screenshots.store(entry.id, source)
                        }
                    }
                    RedirectPage("/entries")
                } }, { error -> either {
                    val id = call.parameterInt("id").bind()
                    val files = app.files.getAllVersions(id).bind()
                    val screenshot = app.screenshots.get(id).map { "/entries/{id}/screenshot.jpg" }
                    EditEntryPage.render(app, submitRequest.bind().with(error), files, screenshot)
                }.flatten() })
            }

            post("/entries/{id}/screenshot") {
                val maybeUser = call.userSession()
                val screenshotRequest = Form.fromParameters<NewScreenshot>(call.receiveMultipart())

                call.respondEither({ either {
                    val user = maybeUser.bind()
                    val form = screenshotRequest.bind().validated().bind()
                    val entryId = call.parameterInt("id").bind()
                    val entry = app.entries.get(entryId, user.id).bind()

                    app.compos.assertCanSubmit(entry.compoId, user.isAdmin).bind()
                    app.screenshots.store(entry.id, form.file)

                    RedirectPage("/entries/$entryId")
                }})
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
