package party.jml.partyboi

import arrow.core.getOrElse
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.assets.admin.configureAdminAssetsRouting
import party.jml.partyboi.compos.admin.configureAdminComposRouting
import party.jml.partyboi.schedule.admin.configureAdminScheduleRouting
import party.jml.partyboi.screen.admin.configureAdminScreenRouting
import party.jml.partyboi.settings.configureSettingsRouting
import party.jml.partyboi.auth.configureAuthentication
import party.jml.partyboi.auth.configureLoginRouting
import party.jml.partyboi.compos.configureComposRouting
import party.jml.partyboi.db.getDatabasePool
import party.jml.partyboi.entries.configureEntriesRouting
import party.jml.partyboi.plugins.configureDefaultRouting
import party.jml.partyboi.plugins.configureHTTP
import party.jml.partyboi.plugins.configureSerialization
import party.jml.partyboi.assets.configureStaticContent
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.Migrations
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.frontpage.configureFrontPageRouting
import party.jml.partyboi.qrcode.configureQrCodeRouting
import party.jml.partyboi.replication.configureReplicationRouting
import party.jml.partyboi.schedule.configureScheduleRouting
import party.jml.partyboi.screen.configureScreenRouting
import party.jml.partyboi.signals.configureSignalRouting
import party.jml.partyboi.voting.configureVotingRouting
import party.jml.partyboi.users.configureUserMgmtRouting
import party.jml.partyboi.voting.admin.configureAdminVotingRouting

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val db = getDatabasePool()
    val migration = runBlocking {
        Migrations.migrate(db).getOrElse { it.throwError() }
    }
    val app = AppServices(db)
    app.replication.setSchemaVersion(migration.targetSchemaVersion ?: migration.initialSchemaVersion)

    launch { app.triggers.start() }

    configureSerialization()
    configureHTTP()
    configureAuthentication(app)

    configureDefaultRouting()
    configureFrontPageRouting(app)
    configureLoginRouting(app)
    configureEntriesRouting(app)
    configureComposRouting(app)
    configureVotingRouting(app)
    configureScreenRouting(app)
    configureQrCodeRouting()
    configureScheduleRouting(app)
    configureAdminAssetsRouting(app)
    configureSignalRouting(app)
    configureSettingsRouting(app)

    configureStaticContent()

    configureAdminComposRouting(app)
    configureAdminScreenRouting(app)
    configureAdminScheduleRouting(app)
    configureUserMgmtRouting(app)
    configureAdminVotingRouting(app)
    configureReplicationRouting(app)

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