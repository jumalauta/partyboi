package party.jml.partyboi.sync

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither

fun Application.configureSyncRouting(app: AppServices) {
    adminRouting {
        get("/admin/sync") {
            call.respondEither {
                val configState = app.sync.configurationState().bind()
                val hosts = if (configState == SymmetricDsConfigurationState.MISSING) emptyList() else {
                    app.sync.getHosts().bind()
                }
                val tz = app.time.timeZone.get().bind()
                SyncAdminPage.render(configState, hosts, tz)
            }
        }

        post("/admin/sync/init") {
            call.respondEither {
                app.sync.configureMaster().bind()
                Redirection("/admin/sync")
            }
        }
    }
}