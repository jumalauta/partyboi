package party.jml.partyboi.replication

import arrow.core.raise.either
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

    routing {
        get("/replication/test") {
            val result = either {
                val data = app.replication.export().bind()
                app.replication.import(data).bind()
            }.mapLeft { it.message }
            call.respond("$result")
        }
    }
}