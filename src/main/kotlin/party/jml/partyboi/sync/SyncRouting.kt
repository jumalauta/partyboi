package party.jml.partyboi.sync

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.launch
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.syncRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.jsonRespond
import party.jml.partyboi.data.parameterEnum
import party.jml.partyboi.data.parameterUUID
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.messages.MessageType
import party.jml.partyboi.system.fullHost
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureSyncRouting(app: AppServices) {
    suspend fun renderPage(remoteForm: Form<RemoteInstance>? = null) = either {
        val apiKey = app.sync.expectedApiKey.get().bind()
        val remoteInstance = app.sync.remoteInstance.get().bind()
        val syncLog = app.sync.getLog().bind()

        SyncPage.render(
            apiKey,
            remoteInstance != null,
            remoteForm ?: Form.of(remoteInstance?.copy(apiToken = "") ?: RemoteInstance.EMPTY),
            syncLog
        )
    }

    adminRouting {
        get("/sync") {
            call.respondEither { renderPage().bind() }
        }

        post("/sync/new-token") {
            call.respondEither {
                val token = app.sync.generateNewToken().bind()
                val fullHost = call.request.fullHost()
                SyncPage.renderNewToken(fullHost, token)
            }
        }

        post("/sync/remote") {
            call.processForm<RemoteInstance>(
                { form ->
                    app.sync.remoteInstance.set(form).bind()
                    app.messages.sendMessage(
                        call.userSession(app).bind().id,
                        MessageType.INFO,
                        "Remote sync configuration saved"
                    )
                    Redirection("/sync")
                },
                { form ->
                    renderPage(form).bind()
                }
            )
        }

        get("/sync/download") {
            call.respondEither {
                val userId = call.userSession(app).bind().id
                launch {
                    app.sync.syncDown().bind()
                    app.messages.sendMessage(
                        userId,
                        MessageType.SUCCESS,
                        "Remote server synced successfully"
                    )
                }
                Redirection("/sync")
            }
        }

        get("/sync/upload") {
            call.respondEither {
                val userId = call.userSession(app).bind().id
                launch {
                    either {
                        app.sync.syncUp().bind()
                        app.messages.sendMessage(
                            userId,
                            MessageType.SUCCESS,
                            "Data and files sent to remote server successfully"
                        )
                    }
                }
                Redirection("/sync")
            }
        }
    }

    syncRouting {
        get("/sync/table/{name}") {
            call.jsonRespond {
                val name = call.parameterEnum<SyncedTable>("name").bind()
                app.sync.getTable(name).bind()
            }
        }

        post("/sync/table") {
            call.jsonRespond {
                val table = call.receive<Table>()
                app.sync.putTable(table).bind()
                "OK"
            }
        }

        get("/sync/missing-files") {
            call.jsonRespond {
                MissingFiles.of(app.sync.getMissingFiles().bind())
            }
        }

        get("/sync/file/{fileId}") {
            either {
                val fileId = call.parameterUUID("fileId").bind()
                app.files.getById(fileId).bind()
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

        post("/sync/file/{fileId}") {
            either {
                val fileId = call.parameterUUID("fileId").bind()
                val targetFile = app.files.getStorageFile(fileId)
                val multipart = call.receiveMultipart()

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem ->
                            targetFile.outputStream().use { output ->
                                part.provider().copyTo(output)
                            }

                        else -> Unit
                    }
                    part.dispose()
                }
            }
        }
    }
}