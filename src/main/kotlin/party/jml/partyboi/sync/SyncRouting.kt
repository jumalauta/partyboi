package party.jml.partyboi.sync

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

        SyncPage.render(
            apiKey,
            remoteInstance != null,
            remoteForm ?: Form.of(remoteInstance?.copy(apiToken = "") ?: RemoteInstance.EMPTY)
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

        get("/sync/run") {
            call.respondEither {
                app.sync.run().bind()
                app.messages.sendMessage(
                    call.userSession(app).bind().id,
                    MessageType.SUCCESS,
                    "Databases synced successfully"
                )
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
    }
}