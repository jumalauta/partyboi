package party.jml.partyboi.sync

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.randomStringId
import party.jml.partyboi.data.switchApiString
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither

fun Application.configureSyncRouting(app: AppServices) {
    adminRouting {
        get("/admin/sync") {
            call.respondEither {
                val configState = app.sync.configurationState().bind()
                val host = if (configState == SymmetricDsConfigurationState.MISSING) null else {
                    app.sync.getHost().getOrNull()
                }
                val tz = app.time.timeZone.get().bind()
                val newNodeForm = Form.of(NewNodeForm.Empty)
                val nodeSecurities = app.sync.getClientNodeSecurities().bind()

                SyncAdminPage.render(configState, host, tz, nodeSecurities, newNodeForm)
            }
        }

        post("/admin/sync/init") {
            call.respondEither {
                app.sync.configureMaster().bind()
                Redirection("/admin/sync")
            }
        }

        post("/admin/sync") {
            call.processForm<NewNodeForm>(
                { node ->
                    app.sync.addClientNode(node.nodeId, randomStringId(50)).bind()
                    Redirection("/admin/sync")
                },
                { error ->
                    Redirection("/admin/sync") // TOOD
                }
            )
        }

        put("/admin/sync/{id}/syncEnabled/{state}") {
            call.switchApiString { id, state -> app.sync.setSyncEnabled(id, state) }
        }
    }
}