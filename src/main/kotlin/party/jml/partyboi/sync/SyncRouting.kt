package party.jml.partyboi.sync

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.data.jsonRespond
import party.jml.partyboi.data.parameterString

fun Application.configureSyncRouting(app: AppServices) {
    adminApiRouting {
        get("/sync/table/{name}") {
            call.jsonRespond {
                val name = call.parameterString("name").bind()
                app.sync.db.getTable(name).bind()
            }
        }
    }
}