package party.jml.partyboi

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.auth.configureAuthentication
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.plugins.configureDefaultRouting
import party.jml.partyboi.plugins.configureHTTP
import party.jml.partyboi.plugins.configureSerialization
import party.jml.partyboi.telnet.runSocketServer

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val app = runBlocking { services() }

    launch { app.triggers.start() }
    launch { app.votes.start() }

    configureSerialization()
    configureHTTP()
    configureAuthentication(app)
    configureDefaultRouting(app)

    launch { app.screen.start() }

    if (app.replication.isReadReplica) {
        launch { app.replication.sync() }
    }

    configureHealthCheck(app)

    launch {
        runSocketServer(9002, app)
    }
}

fun Application.configureHealthCheck(app: AppServices) {
    routing {
        get("/health") {
            val result = app.db.use { it.one(queryOf("SELECT true").map(asBoolean)) }
            call.apiRespond(result.map {})
        }
    }
}