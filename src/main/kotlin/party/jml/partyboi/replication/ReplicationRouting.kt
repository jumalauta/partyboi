package party.jml.partyboi.replication

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.replicationRouting
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterPath
import party.jml.partyboi.data.processFileETag

fun Application.configureReplicationRouting(app: AppServices) {
    replicationRouting {
        get("/replication/export") {
            app.replication.export().fold(
                {
                    call.respond(it.statusCode, it.message)
                },
                { call.respond(it) }
            )
        }

        get("/replication/entry/{storageName...}") {
            val storageName = call.parameterPath("storageName", { app.files.getStoragePath(it) })
            call.processFileETag(storageName) {
                either {
                    app.files.getStorageFile(storageName.bind())
                }.fold(
                    { call.respond(it.statusCode, it.message) },
                    { call.respondFile(it) }
                )
            }
        }

        get("/replication/screenshot/{entryId}") {
            val file = call.parameterPath("entryId", { app.screenshots.getFile(it.toInt()) })
            call.processFileETag(file) {
                file.fold(
                    { call.respond(it.statusCode, it.message) },
                    { call.respondFile(it.toFile()) }
                )
            }
        }

        get("/replication/asset/{name}") {
            val asset = call.parameterPath("name", { app.assets.getFile(it) })
            call.processFileETag(asset) {
                asset.fold(
                    { call.respond(it.statusCode, it.message) },
                    { call.respondFile(it.toFile()) }
                )
            }
        }
    }

    adminRouting {
        get("/replication/sync") {
            call.apiRespond(app.replication.sync())
        }
    }
}