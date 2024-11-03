package party.jml.partyboi.admin.screen

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.*
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.Slide
import party.jml.partyboi.screen.slides.ImageSlide
import party.jml.partyboi.screen.slides.QrCodeSlide
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.templates.*

fun Application.configureAdminScreenRouting(app: AppServices) {
    routing {
        fun renderAdHocEdit(form: Form<*>? = null): Page {
            val current = app.screen.currentSlide()
            return AdminScreenPage.renderAdHocForm(
                form = form ?: current.getForm(),
                currentlyRunning = app.screen.currentState().first.slideSet == "adhoc"
            )
        }

        fun renderSlideSetPage(slideSetName: Either<AppError, String>) = either {
            val slides = app.screen.getSlideSet(slideSetName.bind()).bind()
            val forms = slides.map(SlideEditData.fromRow)
            val (state, isRunning) = app.screen.currentState()
            AdminScreenPage.renderSlideSetForms(slideSetName.bind(), state, isRunning, forms)
        }

        fun renderSlideEdit(
            slideSetName: Either<AppError, String>,
            slideId: Either<AppError, Int>,
            errors: AppError? = null
        ) = either {
            val slide = app.screen.getSlide(slideId.bind()).bind()
            AdminScreenPage.renderSlideForm(
                slideSet = slideSetName.bind(),
                slide = SlideEditData.fromRow(slide),
                triggers = app.triggers.getTriggersForSignal(slide.whenShown()).bind(),
                assetImages = app.assets.getList(FileDesc.IMAGE),
                errors = errors,
            )
        }

        authenticate("admin") {
            fun redirectionToSet(name: String) = Redirection("/admin/screen/$name")

            get("/admin/screen") {
                call.respondRedirect("/admin/screen/adhoc")
            }

            get("/admin/screen/adhoc") {
                call.respondPage(renderAdHocEdit())
            }

            post("/admin/screen/adhoc") {
                call.processForm<TextSlide>(
                    { app.screen.addAdHoc(it).map { redirectionToSet("adhoc") } },
                    { renderAdHocEdit(it).right() }
                )
            }

            get("/admin/screen/{slideSet}") {
                call.respondEither({
                    renderSlideSetPage(call.parameterString("slideSet"))
                })
            }

            get("/admin/screen/{slideSet}/presentation") {
                // TODO: Presentation mode will be removed
                call.respondEither({
                    either {
                        val slideSetName = call.parameterString("slideSet").bind()
                        val slides = app.screen.getSlideSet(slideSetName).bind().filter { it.visible }
                        val (state, isRunning) = app.screen.currentState()

                        ScreenPresentation.render(slideSetName, slides, state, isRunning)
                    }
                })
            }

            get("/admin/screen/{slideSet}/{slideId}") {
                call.respondEither({
                    renderSlideEdit(
                        call.parameterString("slideSet"),
                        call.parameterInt("slideId"),
                    )
                })
            }

            post("/admin/screen/{slideSet}/{slideId}/textslide") {
                call.updateSlide<TextSlide>(app) { s, i, e -> renderSlideEdit(s, i, e) }
            }

            post("/admin/screen/{slideSet}/{slideId}/qrcodeslide") {
                call.updateSlide<QrCodeSlide>(app) { s, i, e -> renderSlideEdit(s, i, e) }
            }

            post("/admin/screen/{slideSet}/{slideId}/imageslide") {
                call.updateSlide<ImageSlide>(app) { s, i, e -> renderSlideEdit(s, i, e) }
            }
        }

        authenticate("admin", optional = true) {
            post("/admin/screen/{slideSet}/text") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val slideSetName = call.parameterString("slideSet").bind()
                        app.screen.addEmptySlideToSlideSet(slideSetName, TextSlide.Empty).bind()
                    }
                }
            }

            post("/admin/screen/{slideSet}/qrcode") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val slideSetName = call.parameterString("slideSet").bind()
                        app.screen.addEmptySlideToSlideSet(slideSetName, QrCodeSlide.Empty).bind()
                    }
                }
            }

            post("/admin/screen/{slideSet}/image") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val slideSetName = call.parameterString("slideSet").bind()
                        app.screen.addEmptySlideToSlideSet(slideSetName, ImageSlide.Empty).bind()
                    }
                }
            }

            post("/admin/screen/{slideSet}/start") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val slideSetName = call.parameterString("slideSet").bind()
                        app.screen.startSlideSet(slideSetName).bind()
                    }
                }
            }

            post("/admin/screen/{slideSet}/stop") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        app.screen.stopSlideSet()
                    }
                }
            }

            post("/admin/screen/{slideSet}/{slideId}/show") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val slideId = call.parameterInt("slideId").bind()
                        app.screen.show(slideId).bind()
                    }
                }
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
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val slideSetName = call.parameterString("slideSet").bind()
                        val slideSet = app.screen.getSlideSet(slideSetName).bind()
                        if (slideSet.isNotEmpty()) {
                            app.screen.stopSlideSet()
                            app.screen.show(slideSet.first().id).bind()
                        }
                    }
                }
            }

            post("/admin/screen/{slideSet}/presentation/next") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val slideSetName = call.parameterString("slideSet").bind()
                        val (state) = app.screen.currentState()
                        if (state.slideSet == slideSetName) {
                            app.screen.showNext()
                        }
                    }
                }
            }

        }
    }
}

suspend inline fun <reified T> ApplicationCall.updateSlide(
    app: AppServices,
    crossinline onError: (Either<AppError, String>, Either<AppError, Int>, AppError?) -> Either<AppError, Renderable>
) where
        T : Slide<T>,
        T : Validateable<T> {
    processForm<T>(
        { slide ->
            either {
                val id = parameterInt("slideId").bind()
                val slideSetName = parameterString("slideSet").bind()
                app.screen.update(id, slide).bind()
                Redirection("/admin/screen/$slideSetName")
            }
        },
        { form ->
            onError(
                parameterString("slideSet"),
                parameterInt("slideId"),
                form.error,
            )
        }
    )
}
