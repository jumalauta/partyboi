package party.jml.partyboi.admin.screen

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.TextScreen
import party.jml.partyboi.templates.respondPage

fun Application.configureAdminScreenRouting(app: AppServices) {
    routing {
        authenticate("admin") {
            get("/admin/screen") {
                val current = app.screen.current()
                val adHoc = if (current is TextScreen) current else TextScreen.Empty
                val form = Form(TextScreen::class, adHoc, initial = true)
                call.respondPage(AdminScreenPage.renderAdHoc(form))
            }

            post("/admin/screen/adhoc") {
                val screenRequest = Form.fromParameters<TextScreen>(call.receiveMultipart())
                screenRequest.map {
                    app.screen.show(it.data)
                    call.respondPage(AdminScreenPage.renderAdHoc(it))
                }
            }
        }
    }
}