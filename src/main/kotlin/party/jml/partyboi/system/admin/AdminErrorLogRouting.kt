package party.jml.partyboi.system.admin

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.templates.respondEither

fun Application.configureAdminErrorLogRouting(app: AppServices) {
    adminRouting {
        get("/admin/errors") {
            call.respondEither {
                val errors = app.errors.getErrors(100, 0).bind()
                AdminErrorLogPage.renderList(errors)
            }
        }

        get("/admin/errors/{id}") {
            call.respondEither {
                val errorId = call.parameterInt("id").bind()
                val error = app.errors.getError(errorId).bind()
                AdminErrorLogPage.renderStackTrace(error)
            }
        }

        get("/admin/test") {
            throw Error("Hupsista")
        }
    }
}