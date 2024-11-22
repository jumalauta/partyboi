package party.jml.partyboi

import io.ktor.server.application.*
import kotlinx.coroutines.launch
import party.jml.partyboi.admin.assets.configureAdminAssetsRouting
import party.jml.partyboi.admin.compos.configureAdminComposRouting
import party.jml.partyboi.admin.schedule.configureAdminScheduleRouting
import party.jml.partyboi.admin.screen.configureAdminScreenRouting
import party.jml.partyboi.auth.configureAuthentication
import party.jml.partyboi.auth.configureLoginRouting
import party.jml.partyboi.compos.configureComposRouting
import party.jml.partyboi.db.getDatabasePool
import party.jml.partyboi.entries.configureEntriesRouting
import party.jml.partyboi.plugins.configureDefaultRouting
import party.jml.partyboi.plugins.configureHTTP
import party.jml.partyboi.plugins.configureSerialization
import party.jml.partyboi.assets.configureStaticContent
import party.jml.partyboi.db.Migrations
import party.jml.partyboi.frontpage.configureFrontPageRouting
import party.jml.partyboi.qrcode.configureQrCodeRouting
import party.jml.partyboi.schedule.configureScheduleRouting
import party.jml.partyboi.screen.configureScreenRouting
import party.jml.partyboi.signals.configureSignalRouting
import party.jml.partyboi.voting.configureVotingRouting

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val db = getDatabasePool()
    val app = AppServices(db)

    launch {
        Migrations.migrate(app)
        app.triggers.start()
    }

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

    configureStaticContent()

    configureAdminComposRouting(app)
    configureAdminScreenRouting(app)
    configureAdminScheduleRouting(app)
}
