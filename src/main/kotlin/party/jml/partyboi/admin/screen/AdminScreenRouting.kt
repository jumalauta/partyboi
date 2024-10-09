package party.jml.partyboi.admin.screen

import arrow.core.Some
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.TextScreen
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureAdminScreenRouting(app: AppServices) {
    routing {
        authenticate("admin") {
            get("/admin/screen") {
                call.respondRedirect("/admin/screen/adhoc")
            }

            get("/admin/screen/adhoc") {
                val current = app.screen.current()
                val form = current.getForm()
                val currentlyRunning = app.screen.currentlyRunningCollection()
                call.respondPage(AdminScreenPage.renderAdHocForm(currentlyRunning == Some("adhoc"), form))
            }

            post("/admin/screen/adhoc") {
                val screenRequest = Form.fromParameters<TextScreen>(call.receiveMultipart())
                call.respondEither({ either {
                    val screen = screenRequest.bind()
                    runBlocking { app.screen.addAdHoc(screen.data).bind() }
                    AdminScreenPage.renderAdHocForm(true, screen)
                }})
            }

            get("/admin/screen/{collection}") {
                call.respondEither({ either {
                    val collection = call.parameterString("collection").bind()
                    val screens = app.screen.getCollection(collection).bind()
                    val forms = screens.map { it.content.getForm() }
                    val currentlyRunning = app.screen.currentlyRunningCollection()
                    AdminScreenPage.renderCollectionForms(collection, currentlyRunning, forms)
                }})
            }
        }

        authenticate("admin", optional = true) {
            post("/admin/screen/{collection}/text") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val collection = call.parameterString("collection").bind()
                    app.screen.addEmptyToCollection(collection, TextScreen.Empty).bind()
                } }
            }

            post("/admin/screen/{collection}/start") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val collection = call.parameterString("collection").bind()
                    app.screen.startSlideShow(collection).bind()
                } }
            }
        }
    }
}