package party.jml.partyboi.admin.screen

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
import party.jml.partyboi.screen.slides.QrCodeSlide
import party.jml.partyboi.screen.slides.TextSlide
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
                val current = app.screen.currentSlide()
                val form = current.getForm()
                val currentlyRunning = app.screen.currentState().first.slideSet == "adhoc"
                call.respondPage(AdminScreenPage.renderAdHocForm(currentlyRunning, form))
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
                    val (state, isRunning) = app.screen.currentState()
                    AdminScreenPage.renderSlideSetForms(slideSetName, state, isRunning, forms)
                }})
            }

            get("/admin/screen/{slideSet}/presentation") {
                call.respondEither({ either {
                    val slideSetName = call.parameterString("slideSet").bind()
                    val slides = app.screen.getSlideSet(slideSetName).bind().filter { it.visible }
                    val (state, isRunning) = app.screen.currentState()

                    ScreenPresentation.render(slideSetName, slides, state, isRunning)
                }})
            }

            get("/admin/screen/{slideSet}/{slideId}") {
                call.respondEither({ either {
                    val slideSetName = call.parameterString("slideSet").bind()
                    val slideId = call.parameterInt("slideId").bind()
                    val slide = app.screen.getSlide(slideId).bind()
                    val form = SlideEditData.fromRow(slide)
                    val actions = app.triggers.getTriggersForSignal(slide.whenShown()).bind()
                    AdminScreenPage.renderSlideForm(slideSetName, form, actions)
                }})
            }

            post("/admin/screen/{slideSet}/{slideId}/textslide") {
                val slideRequest = Form.fromParameters<TextSlide>(call.receiveMultipart())
                call.respondEither({ either {
                    val id = call.parameterInt("slideId").bind()
                    val slideSetName = call.parameterString("slideSet").bind()
                    val slide = slideRequest.bind().data
                    runBlocking { app.screen.update(id, slide) }
                    RedirectPage("/admin/screen/${slideSetName}")
                }})
            }

            post("/admin/screen/{slideSet}/{slideId}/qrcodeslide") {
                val slideRequest = Form.fromParameters<QrCodeSlide>(call.receiveMultipart())
                call.respondEither({ either {
                    val id = call.parameterInt("slideId").bind()
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

            post("/admin/screen/{slideSet}/qrcode") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val slideSetName = call.parameterString("slideSet").bind()
                    app.screen.addEmptySlideToSlideSet(slideSetName, QrCodeSlide.Empty).bind()
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

            post("/admin/screen/{slideSet}/{slideId}/show") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val slideId = call.parameterInt("slideId").bind()
                    app.screen.show(slideId).bind()
                } }
            }

            put("/admin/screen/{id}/setVisible/{state}") {
               call.switchApi { id, visible -> app.screen.setVisible(id, visible) }
            }

            put("/admin/screen/{slideSet}/runOrder") {
                call.receive<List<String>>()
                    .mapIndexed { index, slideId -> app.screen.setRunOrder(slideId.toInt(), index) }
                call.respondText("OK")
            }

            post("/admin/screen/{slideSet}/presentation/start") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val slideSetName = call.parameterString("slideSet").bind()
                    val slideSet = app.screen.getSlideSet(slideSetName).bind()
                    if (slideSet.isNotEmpty()) {
                        app.screen.stopSlideSet()
                        app.screen.show(slideSet.first().id).bind()
                    }
                } }
            }

            post("/admin/screen/{slideSet}/presentation/next") {
                call.apiRespond { either {
                    call.userSession().bind()
                    val slideSetName = call.parameterString("slideSet").bind()
                    val (state) = app.screen.currentState()
                    if (state.slideSet == slideSetName) {
                        app.screen.showNext()
                    }
                } }
            }

        }
    }
}