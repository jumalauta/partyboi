package party.jml.partyboi.sync

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.syncRouting
import party.jml.partyboi.data.jsonRespond
import party.jml.partyboi.data.parameterEnum
import party.jml.partyboi.templates.HtmlString
import party.jml.partyboi.templates.respondEither

fun Application.configureSyncRouting(app: AppServices) {
    adminRouting {
        get("/sync/run") {
            call.respondEither {
                app.sync.run().bind()
                HtmlString("OK")
            }
        }
    }

    syncRouting {
        get("/sync/table/{name}") {
            call.jsonRespond {
                val name = call.parameterEnum<SyncedTable>("name").bind()
                app.sync.getTable(name).bind()
            }
        }
    }
}