package party.jml.partyboi

import io.ktor.server.application.*
import party.jml.partyboi.admin.compos.configureAdminComposRouting
import party.jml.partyboi.admin.schedule.configureAdminScheduleRouting
import party.jml.partyboi.admin.screen.configureAdminScreenRouting
import party.jml.partyboi.auth.configureAuthentication
import party.jml.partyboi.auth.configureLoginRouting
import party.jml.partyboi.compos.configureComposRouting
import party.jml.partyboi.data.getDatabasePool
import party.jml.partyboi.entries.configureEntriesRouting
import party.jml.partyboi.plugins.configureDefaultRouting
import party.jml.partyboi.plugins.configureHTTP
import party.jml.partyboi.plugins.configureSerialization
import party.jml.partyboi.assets.configureStaticContent
import party.jml.partyboi.qrcode.configureQrCodeRouting
import party.jml.partyboi.schedule.configureScheduleRouting
import party.jml.partyboi.screen.configureScreenRouting
import party.jml.partyboi.templates.configureStyles
import party.jml.partyboi.voting.configureVotingRouting

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}
fun Application.module() {
    val db = getDatabasePool()
    val app = AppServices(db)

    configureSerialization()
    configureHTTP()
    configureAuthentication(app)
    configureStyles()
    configureStaticContent()

    configureDefaultRouting()
    configureLoginRouting(app)
    configureEntriesRouting(app)
    configureComposRouting(app)
    configureVotingRouting(app)
    configureScreenRouting(app)
    configureQrCodeRouting()
    configureScheduleRouting(app)

    configureAdminComposRouting(app)
    configureAdminScreenRouting(app)
    configureAdminScheduleRouting(app)
}
