package party.jml.partyboi

import arrow.core.getOrElse
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.auth.configureAuthentication
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.Migrations
import party.jml.partyboi.db.getDatabasePool
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.plugins.configureDefaultRouting
import party.jml.partyboi.plugins.configureHTTP
import party.jml.partyboi.plugins.configureSerialization

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val db = getDatabasePool()
    val migration = runBlocking {
        Migrations.migrate(db).getOrElse { it.throwError() }
    }
    val app = AppServices(db, config())
    app.replication.setSchemaVersion(migration.targetSchemaVersion ?: migration.initialSchemaVersion)

    launch { app.triggers.start() }

    configureSerialization()
    configureHTTP()
    configureAuthentication(app)
    configureDefaultRouting(app)

    launch {
        app.screen.start()
    }

    if (app.replication.isReadReplica) {
        launch { app.replication.sync() }
    }

    configureHealthCheck(app)
}

fun Application.configureHealthCheck(app: AppServices) {
    routing {
        get("/health") {
            val result = app.db.use { it.one(queryOf("SELECT true").map(asBoolean)) }
            call.apiRespond(result.map {})
        }
    }
}