package party.jml.partyboi.workqueue.admin

import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.parameterUUID
import party.jml.partyboi.templates.respondEither

fun Application.configureAdminTasksRouting(app: AppServices) {
    adminRouting {
        get("/admin/tasks") {
            call.respondEither {
                val tz = app.time.timeZone.get().bind()
                val tasks = app.workQueue.repository.list().bind()
                AdminTasksPage.renderList(tasks, tz)
            }
        }

        get("/admin/tasks/{id}") {
            call.respondEither {
                val tz = app.time.timeZone.get().bind()
                val id = call.parameterUUID("id").bind()
                val task = app.workQueue.repository.getById(id).bind()
                AdminTasksPage.renderDetails(task, tz)
            }
        }
    }
}
