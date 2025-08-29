package party.jml.partyboi.screen.admin

import arrow.core.getOrElse
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.*
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.SlideSetRow
import party.jml.partyboi.screen.slides.*
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.toDate
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.validation.Validateable

fun Application.configureAdminScreenRouting(app: AppServices) {
    suspend fun renderAdHocEdit(form: Form<*>? = null): AppResult<Page> = either {
        AdminScreenPage.renderAdHocForm(
            form = form ?: app.screen.getAddHoc().bind()
                .map { it.getSlide().getForm() }
                .getOrElse { Form(TextSlide::class, TextSlide.Empty, true) },
            slideSets = app.screen.getSlideSets().bind(),
        )
    }

    suspend fun renderSlideSetPage(slideSetName: AppResult<String>) = either {
        val slides = app.screen.getSlideSet(slideSetName.bind()).bind()
        val (state, isRunning) = app.screen.currentState()
        AdminScreenPage.renderSlideSetForms(
            slideSet = slideSetName.bind(),
            screenState = state,
            isRunning = isRunning,
            slides = slides.map(SlideEditData.fromRow),
            slideSets = app.screen.getSlideSets().bind(),
        )
    }

    suspend fun renderSlideEdit(
        slideSetName: AppResult<String>,
        slideId: AppResult<Int>,
        errors: AppError? = null,
    ) = either {
        val slide = app.screen.getSlide(slideId.bind()).bind()
        AdminScreenPage.renderSlideForm(
            slideSet = slideSetName.bind(),
            slide = SlideEditData.fromRow(slide),
            triggers = app.triggers.getTriggersForSignal(slide.whenShown()).bind(),
            assetImages = app.assets.getList(FileDesc.IMAGE),
            errors = errors,
            slideSets = app.screen.getSlideSets().bind(),
        )
    }

    adminRouting {
        fun redirectionToSet(name: String) = Redirection("/admin/screen/$name")

        get("/admin/screen") {
            call.respondRedirect("/admin/screen/${SlideSetRow.DEFAULT}")
        }

        get("/admin/screen/adhoc") {
            call.respondEither { renderAdHocEdit().bind() }
        }

        post("/admin/screen/adhoc") {
            call.processForm<TextSlide>(
                { app.screen.addAdHoc(it).map { redirectionToSet("adhoc") }.bind() },
                { renderAdHocEdit(it).bind() }
            )
        }

        get("/admin/screen/{slideSet}") {
            call.respondEither {
                renderSlideSetPage(call.parameterString("slideSet")).bind()
            }
        }

        get("/admin/screen/{slideSet}/{slideId}") {
            call.respondEither {
                renderSlideEdit(
                    call.parameterString("slideSet"),
                    call.parameterInt("slideId"),
                ).bind()
            }
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

    adminApiRouting {
        post("/admin/screen/{slideSet}/text") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                app.screen.addSlide(slideSetName, TextSlide.Empty).bind()
            }
        }

        post("/admin/screen/{slideSet}/qrcode") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                app.screen.addSlide(slideSetName, QrCodeSlide.Empty).bind()
            }
        }

        post("/admin/screen/{slideSet}/image") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                app.screen.addSlide(slideSetName, ImageSlide.Empty).bind()
            }
        }

        post("/admin/screen/{slideSet}/schedule") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                val events = app.events.getPublic().bind()
                val allDates = events.map { it.startTime.toDate() }.distinct().sorted()
                val existingSlides = app.screen.getSlideSet(slideSetName).bind()
                val existingDates = existingSlides.flatMap {
                    val slide = it.getSlide()
                    when (slide) {
                        is ScheduleSlide -> listOf(slide.date)
                        else -> emptyList()
                    }
                }.distinct()

                allDates.minus(existingDates).forEach { date ->
                    app.screen.addSlide(slideSetName, ScheduleSlide(date)).bind()
                }
            }
        }

        post("/admin/screen/{slideSet}/start") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                app.screen.startSlideSet(slideSetName).bind()
            }
        }

        post("/admin/screen/{slideSet}/stop") {
            call.apiRespond {
                call.userSession(app).bind()
                app.screen.stopSlideSet()
            }
        }

        post("/admin/screen/{slideSet}/next") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                app.screen.showNextSlideFromSet(slideSetName).bind()
            }
        }

        post("/admin/screen/{slideSet}/{slideId}/show") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideId = call.parameterInt("slideId").bind()
                app.screen.show(slideId).bind()
            }
        }

        put("/admin/screen/{id}/setVisible/{state}") {
            call.switchApi { id, visible -> app.screen.setVisible(id, visible) }
        }

        put("/admin/screen/{id}/showOnInfo/{state}") {
            call.switchApi { id, show -> app.screen.showOnInfo(id, show) }
        }

        post("/admin/screen/{slideSet}/runOrder") {
            val newOrder = call.receive<List<String>>()
            call.apiRespond {
                call.parameterString("slideSet").bind()
                call.userSession(app).bind()
                newOrder
                    .mapIndexed { index, slideId -> app.screen.setRunOrder(slideId.toInt(), index) }
                    .bindAll()
            }
        }

        post("/admin/screen/{slideSet}/presentation/start") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                val slideSet = app.screen.getSlideSet(slideSetName).bind()
                if (slideSet.isNotEmpty()) {
                    app.screen.stopSlideSet()
                    app.screen.show(slideSet.first().id).bind()
                }
            }
        }

        post("/admin/screen/{slideSet}/presentation/next") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                val (state) = app.screen.currentState()
                if (state.slideSet == slideSetName) {
                    app.screen.showNext()
                }
            }
        }

        delete("/admin/screen/{slideId}") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideId = call.parameterInt("slideId").bind()
                app.screen.delete(slideId).bind()
            }
        }
    }
}

suspend inline fun <reified T> ApplicationCall.updateSlide(
    app: AppServices,
    crossinline onError: suspend (AppResult<String>, AppResult<Int>, AppError?) -> AppResult<Renderable>
) where
        T : Slide<T>,
        T : Validateable<T> {
    processForm<T>(
        { slide ->
            val id = parameterInt("slideId").bind()
            val slideSetName = parameterString("slideSet").bind()
            app.screen.update(id, slide).bind()
            Redirection("/admin/screen/$slideSetName")
        },
        { form ->
            onError(
                parameterString("slideSet"),
                parameterInt("slideId"),
                form.error,
            ).bind()
        }
    )
}
