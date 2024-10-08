package party.jml.partyboi.admin.screen

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.AdHocScreen
import party.jml.partyboi.templates.respondPage

fun Application.configureAdminScreenRouting(app: AppServices) {
    routing {
        authenticate("admin") {
            get("/admin/screen") {
                val current = app.screen.current()
                val adHoc = if (current is AdHocScreen) current else AdHocScreen.Empty
                val form = Form(AdHocScreen::class, adHoc, initial = true)
                call.respondPage(AdminScreenPage.render(form))
            }

            post("/admin/screen/adhoc") {
                val screenRequest = Form.fromParameters<AdHocScreen>(call.receiveMultipart())
                screenRequest.map {
                    app.screen.show(it.data)
                    call.respondPage(AdminScreenPage.render(it))
                }
            }
        }
    }
}