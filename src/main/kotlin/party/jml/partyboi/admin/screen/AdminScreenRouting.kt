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
import party.jml.partyboi.data.parameterInt
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.data.switchApi
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.TextSlide
import party.jml.partyboi.templates.RedirectPage
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
                val currentlyRunning = app.screen.currentlyRunningSlideSet()
                call.respondPage(AdminScreenPage.renderAdHocForm(currentlyRunning == Some("adhoc"), form))
            }

            post("/admin/screen/adhoc") {
                val screenRequest = Form.fromParameters<TextSlide>(call.receiveMultipart())
                call.respondEither({ either {
                    val screen = screenRequest.bind()
                    runBlocking { app.screen.addAdHoc(screen.data).bind() }
                    AdminScreenPage.renderAdHocForm(true, screen)
                }})
            }

            get("/admin/screen/{slideSet}") {
                call.respondEither({ either {
                    val slideSetName = call.parameterString("slideSet").bind()
                    val slides = app.screen.getSlideSet(slideSetName).bind()
                    val forms = slides.map(SlideEditData.fromRow)
                    val currentlyRunning = app.screen.currentlyRunningSlideSet()
                    AdminScreenPage.renderSlideSetForms(slideSetName, currentlyRunning, forms)
                }})
            }

            post("/admin/screen/{slideSet}/{id}/textscreen") {
                val slideRequest = Form.fromParameters<TextSlide>(call.receiveMultipart())
                call.respondEither({ either {
                    val id = call.parameterInt("id").bind()
                    val slideSetName = call.parameterString("slideSet").bind()
                    val slide = slideRequest.bind().data
                    runBlocking { app.screen.update(id, slide) }
                    RedirectPage("/admin/screen/${slideSetName}")
                }})
            }
        }

        authenticate("admin", optional = true) {
            post("/admin/screen/{slideSet}/text") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val slideSetName = call.parameterString("slideSet").bind()
                    app.screen.addEmptySlideToSlideSet(slideSetName, TextSlide.Empty).bind()
                } }
            }

            post("/admin/screen/{slideSet}/start") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val slideSetName = call.parameterString("slideSet").bind()
                    app.screen.startSlideSet(slideSetName).bind()
                } }
            }

            post("/admin/screen/{slideSet}/stop") {
                call.apiRespond { either {
                    call.userSession().bind()
                    app.screen.stopSlideSet()
                } }
            }

            put("/admin/screen/{id}/setVisible/{state}") {
               call.switchApi { id, visible -> app.screen.setVisible(id, visible) }
            }
        }
    }
}