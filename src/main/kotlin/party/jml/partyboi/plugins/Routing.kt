package party.jml.partyboi.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.util.*
import kotlinx.html.h1
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.assets.admin.configureAdminAssetsRouting
import party.jml.partyboi.assets.configureStaticContent
import party.jml.partyboi.auth.configureLoginRouting
import party.jml.partyboi.compos.admin.configureAdminComposRouting
import party.jml.partyboi.compos.configureComposRouting
import party.jml.partyboi.entries.configureEntriesRouting
import party.jml.partyboi.frontpage.configureFrontPageRouting
import party.jml.partyboi.qrcode.configureQrCodeRouting
import party.jml.partyboi.replication.configureReplicationRouting
import party.jml.partyboi.schedule.admin.configureAdminScheduleRouting
import party.jml.partyboi.schedule.configureScheduleRouting
import party.jml.partyboi.screen.admin.configureAdminScreenRouting
import party.jml.partyboi.screen.configureScreenRouting
import party.jml.partyboi.settings.configureSettingsRouting
import party.jml.partyboi.signals.configureSignalRouting
import party.jml.partyboi.system.admin.configureAdminErrorLogRouting
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.errorMessage
import party.jml.partyboi.templates.respondPage
import party.jml.partyboi.users.configureUserMgmtRouting
import party.jml.partyboi.voting.admin.configureAdminVotingRouting
import party.jml.partyboi.voting.configureVotingRouting

@Serializable
data class ErrorContext(
    val action: String,
    val headers: Map<String, List<String>>,
    val queryParameters: Map<String, List<String>>
) {
    companion object {
        fun of(call: ApplicationCall) = ErrorContext(
            action = "${call.request.httpMethod.value} ${call.request.uri}",
            headers = call.request.headers.toMap(),
            queryParameters = call.request.queryParameters.toMap()
        )
    }
}

fun Application.configureDefaultRouting(app: AppServices) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            if (cause !is io.ktor.util.cio.ChannelWriteException) {
                val errorKey = app.errors.saveSafely(cause, ErrorContext.of(call))
                call.respondPage(Page("Error") {
                    h1 { +"Oh noes..." }
                    errorMessage(errorKey, cause)
                })
            }
        }
    }
    install(AutoHeadResponse)

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
    configureAdminErrorLogRouting(app)
}
