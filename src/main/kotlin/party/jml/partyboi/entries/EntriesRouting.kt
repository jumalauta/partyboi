@file:Suppress("EscapedRaise")

package party.jml.partyboi.entries

import arrow.core.Either
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
import party.jml.partyboi.auth.User
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureEntriesRouting(app: AppServices) {
    fun renderEntriesPage(
        userSession: Either<AppError, User>,
        newEntryForm: Form<NewEntry>? = null,
    ) = either {
        val user = userSession.bind()
        val userEntries = app.entries.getUserEntries(user.id).bind()
        val screenshots = app.screenshots.getForEntries(userEntries)

        EntriesPage.render(
            newEntryForm = newEntryForm ?: Form(NewEntry::class, NewEntry.Empty, initial = true),
            compos = app.compos.getAllCompos().bind().filter { it.canSubmit(user) },
            userEntries = userEntries,
            screenshots = screenshots,
        )
    }

    fun renderEditEntryPage(
        entryId: Either<AppError, Int>,
        user: Either<AppError, User>,
        entryUpdateForm: Form<EntryUpdate>? = null,
        screenshotForm: Form<NewScreenshot>? = null,
    ) = either {
        val files = app.files.getAllVersions(entryId.bind()).bind()
        val screenshotUrl = app.screenshots.get(entryId.bind()).map { it.externalUrl() }
        val entry = app.entries.get(entryId.bind(), user.bind().id).bind()
        val compos = app.compos.getAllCompos().bind().filter { it.canSubmit(user.bind()) || it.id == entry.compoId }

        EditEntryPage.render(
            entryUpdateForm = entryUpdateForm ?: Form(
                EntryUpdate::class,
                EntryUpdate.fromEntry(entry),
                initial = true
            ),
            screenshotForm = screenshotForm ?: Form(NewScreenshot::class, NewScreenshot.Empty, true),
            compos = compos,
            files = files,
            screenshot = screenshotUrl,
        )
    }

    routing {
        authenticate("user") {
            get("/entries") {
                call.respondEither({ renderEntriesPage(call.userSession()) })
            }

            get("/entries/submit/{compoId}") {
                call.respondEither({
                    either {
                        val compoId = call.parameterInt("compoId").bind()
                        val form = Form(NewEntry::class, NewEntry.Empty.copy(compoId = compoId), initial = true)
                        renderEntriesPage(call.userSession(), form).bind()
                    }
                })
            }

            post("/entries") {
                call.processForm<NewEntry>(
                    { newEntry ->
                        either {
                            val user = call.userSession().bind()
                            val entry = newEntry.copy(userId = user.id)
                            app.compos.assertCanSubmit(entry.compoId, user.isAdmin).bind()
                            app.entries.add(entry).bind()
                            Redirection("/entries")
                        }
                    },
                    {
                        renderEntriesPage(call.userSession(), newEntryForm = it)
                    }
                )
            }

            get("/entries/{id}") {
                call.respondEither({
                    renderEditEntryPage(call.parameterInt("id"), call.userSession())
                })
            }

            get("/entries/{id}/screenshot.jpg") {
                either {
                    val id = call.parameterInt("id").bind()
                    app.screenshots.get(id)
                        .toEither { NotFound("Entry screenshot not found") }
                        .bind()
                }.fold(
                    { call.respondPage(it) },
                    { call.respondFile(it.systemPath.toFile()) }
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
                call.processForm<EntryUpdate>(
                    { entry ->
                        either {
                            val user = call.userSession().bind()

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

                            Redirection("/entries")
                        }
                    },
                    {
                        renderEditEntryPage(call.parameterInt("id"), call.userSession(), entryUpdateForm = it)
                    }
                )
            }

            post("/entries/{id}/screenshot") {
                call.processForm<NewScreenshot>(
                    { screenshot ->
                        either {
                            val user = call.userSession().bind()
                            val entryId = call.parameterInt("id").bind()
                            val entry = app.entries.get(entryId, user.id).bind()

                            app.compos.assertCanSubmit(entry.compoId, user.isAdmin).bind()
                            app.screenshots.store(entry.id, screenshot.file)

                            Redirection("/entries/$entryId")
                        }
                    },
                    { renderEditEntryPage(call.parameterInt("id"), call.userSession(), screenshotForm = it) }
                )
            }
        }

        // API routes (we don't want to redirect user to login page)
        authenticate("user", optional = true) {
            delete("/entries/{id}") {
                call.apiRespond {
                    either {
                        val user = call.userSession().bind()
                        val id = call.parameterInt("id").bind()
                        app.entries.delete(id, user.id).bind()
                    }
                }
            }
        }
    }
}
