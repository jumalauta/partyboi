package party.jml.partyboi.replication

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.replicationRouting

fun Application.configureReplicationRouting(app: AppServices) {
    replicationRouting {
        get("/replication/export") {
            app.replication.export().fold(
                { call.respond(it.statusCode, it.message) },
                { call.respond(it) }
            )
        }
    }
}