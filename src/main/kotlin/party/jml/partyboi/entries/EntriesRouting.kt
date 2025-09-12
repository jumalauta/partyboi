@file:Suppress("EscapedRaise")

package party.jml.partyboi.entries

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.User
import party.jml.partyboi.auth.userApiRouting
import party.jml.partyboi.auth.userRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage
import java.util.*

fun Application.configureEntriesRouting(app: AppServices) {
    suspend fun renderEntriesPage(
        userSession: AppResult<User>,
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

    suspend fun renderEditEntryPage(
        entryId: AppResult<UUID>,
        user: AppResult<User>,
        entryUpdateForm: Form<EntryUpdate>? = null,
        screenshotForm: Form<NewScreenshot>? = null,
    ) = either {
        val files = app.files.getAllVersions(entryId.bind()).bind()
        val screenshotUrl = app.screenshots.get(entryId.bind()).map { it.externalUrl() }
        val entry = app.entries.get(entryId.bind(), user.bind().id).bind()
        val compos = app.compos.getAllCompos().bind().filter { it.canSubmit(user.bind()) || it.id == entry.compoId }

        val allowEdit =
            compos.find { it.id == entry.compoId }?.allowSubmit == true ||
                    app.entries.assertCanSubmit(
                        entryId.bind(),
                        user.bind().isAdmin
                    ).isRight()

        EditEntryPage.render(
            user = user.bind(),
            entryUpdateForm = entryUpdateForm ?: Form(
                EntryUpdate::class,
                EntryUpdate.fromEntry(entry),
                initial = true
            ),
            screenshotForm = screenshotForm ?: Form(NewScreenshot::class, NewScreenshot.Empty, true),
            compos = compos,
            files = files,
            screenshot = screenshotUrl,
            allowEdit = allowEdit,
        )
    }

    userRouting {
        get("/entries") {
            call.respondEither { renderEntriesPage(call.userSession(app)).bind() }
        }

        get("/entries/submit/{compoId}") {
            call.respondEither {
                val compoId = call.parameterUUID("compoId").bind()
                val form = Form(NewEntry::class, NewEntry.Empty.copy(compoId = compoId), initial = true)
                renderEntriesPage(call.userSession(app), form).bind()
            }
        }

        post("/entries") {
            call.processForm<NewEntry>(
                { newEntry ->
                    val user = call.userSession(app).bind()
                    val entry = newEntry.copy(userId = user.id)
                    app.compos.assertCanSubmit(entry.compoId, user.isAdmin).bind()
                    app.entries.add(entry).bind()
                    Redirection("/")
                },
                {
                    renderEntriesPage(call.userSession(app), newEntryForm = it).bind()
                },
                app.config.maxFileUploadSize
            )
        }

        get("/entries/{id}") {
            call.respondEither {
                renderEditEntryPage(call.parameterUUID("id"), call.userSession(app)).bind()
            }
        }

        get("/entries/{id}/screenshot.jpg") {
            either {
                val id = call.parameterUUID("id").bind()
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
                val id = call.parameterUUID("id").bind()
                val version = call.parameterInt("version").bind()
                val user = call.userSession(app).bind()
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

        get("/entries/download/{entryId}") {
            either {
                val entryId = call.parameterUUID("entryId").bind()
                val entry = app.entries.get(entryId).bind()
                val compo = app.compos.getById(entry.compoId).bind()
                if (compo.publicResults) {
                    app.files.getLatest(entryId, originalsOnly = true).bind()
                } else {
                    raise(NotFound("Downloading this file is disabled until results are public"))
                }
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
                    val user = call.userSession(app).bind()

                    firstRight(
                        app.compos.assertCanSubmit(entry.compoId, user.isAdmin),
                        app.entries.assertCanSubmit(entry.id, user.isAdmin),
                    ).bind()

                    val newEntry = app.entries.update(entry, user.id).bind()

                    if (entry.file.isDefined) {
                        val storageFilename = app.files.makeStorageFilename(newEntry, entry.file.name).bind()
                        entry.file.writeEntry(storageFilename).bind()
                        val file = app.files.add(
                            NewFileDesc(
                                entryId = entry.id,
                                originalFilename = entry.file.name,
                                storageFilename = storageFilename,
                                processed = false,
                                info = null
                            )
                        ).bind()

                        app.screenshots.scanForScreenshotSource(file).map { source ->
                            app.screenshots.store(entry.id, source)
                        }
                    }

                    Redirection("/")
                },
                {
                    renderEditEntryPage(
                        entryId = call.parameterUUID("id"),
                        user = call.userSession(app),
                        entryUpdateForm = it
                    ).bind()
                },
                app.config.maxFileUploadSize
            )
        }

        post("/entries/{id}/screenshot") {
            call.processForm<NewScreenshot>(
                { screenshot ->
                    val user = call.userSession(app).bind()
                    val entryId = call.parameterUUID("id").bind()
                    val entry = app.entries.get(entryId, user.id).bind()

                    app.compos.assertCanSubmit(entry.compoId, user.isAdmin).bind()
                    app.screenshots.store(entry.id, screenshot.file)

                    Redirection("/entries/$entryId")
                },
                {
                    renderEditEntryPage(
                        entryId = call.parameterUUID("id"),
                        user = call.userSession(app),
                        screenshotForm = it
                    ).bind()
                }
            )
        }
    }

    userApiRouting {
        delete("/entries/{id}") {
            call.apiRespond {
                val user = call.userSession(app).bind()
                val id = call.parameterUUID("id").bind()
                app.entries.delete(id, user.id).bind()
            }
        }
    }
}
