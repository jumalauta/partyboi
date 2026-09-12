package party.jml.partyboi

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours
import party.jml.partyboi.auth.configureAuthentication
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.plugins.configureDefaultRouting
import party.jml.partyboi.plugins.configureHTTP
import party.jml.partyboi.plugins.configureSerialization

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val app = runBlocking { services() }

    runBlocking { app.settings.initPartyDatesFromSchedule() }

    launch { app.triggers.start() }
    launch { app.votes.start() }
    launch {
        // read() already ignores expired sessions; this just clears them out of storage over time.
        while (true) {
            app.sessions.purgeExpired()
            delay(6.hours)
        }
    }
    if (app.config.runWorkQueue) {
        launch { app.workQueue.start() }
    }

    configureSerialization()
    configureHTTP()
    configureAuthentication(app)
    configureDefaultRouting(app)

    configureHealthCheck(app)
    app.email.configure(this)
}

fun Application.configureHealthCheck(app: AppServices) {
    routing {
        get("/health") {
            val result = app.db.use { one(queryOf("SELECT true").map(asBoolean)) }
            call.apiRespond(result.map {})
        }
    }
}