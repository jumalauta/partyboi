package party.jml.partyboi.infoscreen.admin

import arrow.core.raise.either
import arrow.core.right
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
import party.jml.partyboi.form.collect
import party.jml.partyboi.infoscreen.NewSlideSet
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.infoscreen.slides.ImageSlide
import party.jml.partyboi.infoscreen.slides.QrCodeSlide
import party.jml.partyboi.infoscreen.slides.Slide
import party.jml.partyboi.infoscreen.slides.TextSlide
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.validation.Validateable
import java.util.*

fun Application.configureAdminScreenRouting(app: AppServices) {
    suspend fun renderAdHocEdit(form: Form<*>? = null): AppResult<Page> = either {
        AdminScreenPage.renderAdHocForm(
            form = form ?: (app.screen.getAddHoc().bind()?.getSlide()?.getForm()
                ?: Form(TextSlide::class, TextSlide.Empty, true)),
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
        slideId: AppResult<UUID>,
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

    // Render an empty edit form for creating a new slide of the given type. The slide
    // isn't persisted yet; the matching POST handler does that only after the admin
    // submits, so abandoning the form leaves no row behind.
    suspend fun renderNewSlideEdit(
        slideSetName: AppResult<String>,
        template: Slide<*>,
        errors: AppError? = null,
    ) = either {
        AdminScreenPage.renderNewSlideForm(
            slideSet = slideSetName.bind(),
            slide = template,
            errors = errors,
            slideSets = app.screen.getSlideSets().bind(),
        )
    }

    // Render the image-slides picker: every image asset not already used by an
    // ImageSlide in this slide set, with an embedded upload form for adding more.
    suspend fun renderImageSlidesPicker(
        slideSetName: AppResult<String>,
        uploadError: String? = null,
    ) = either {
        val slideSet = slideSetName.bind()
        val usedImages = app.screen.getSlideSet(slideSet).bind()
            .mapNotNull { (it.getSlide() as? ImageSlide)?.assetImage }
            .toSet()
        val availableImages = app.assets.getList(FileDesc.IMAGE)
            .filterNot { it.fullName in usedImages }
        AdminScreenPage.renderImageSlidesPicker(
            slideSet = slideSet,
            availableImages = availableImages,
            uploadError = uploadError,
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

        // New-slideset form: registered before the {slideSet} catch-all so Ktor's
        // literal-segment preference routes /admin/screen/new here even if some
        // future migration introduces a slide set called "new".
        get("/admin/screen/new") {
            call.respondEither {
                AdminScreenPage.renderNewSlideSetForm(
                    form = Form(NewSlideSet::class, NewSlideSet.Empty, initial = true),
                    slideSets = app.screen.getSlideSets().bind(),
                )
            }
        }

        post("/admin/screen/new") {
            call.processForm<NewSlideSet>(
                { data ->
                    val id = app.screen.createSlideSet(data.name).bind()
                    Redirection("/admin/screen/$id")
                },
                { form ->
                    AdminScreenPage.renderNewSlideSetForm(
                        form = form,
                        slideSets = app.screen.getSlideSets().bind(),
                    )
                }
            )
        }

        get("/admin/screen/{slideSet}") {
            call.respondEither {
                renderSlideSetPage(call.parameterString("slideSet")).bind()
            }
        }

        // Create flow for text and QR-code slides: the dropdown links here, the form
        // is rendered with an empty template, and only the POST persists the slide.
        get("/admin/screen/{slideSet}/new/textslide") {
            call.respondEither {
                renderNewSlideEdit(call.parameterString("slideSet"), TextSlide.Empty).bind()
            }
        }

        post("/admin/screen/{slideSet}/new/textslide") {
            call.createSlide<TextSlide>(app) { s, e -> renderNewSlideEdit(s, TextSlide.Empty, e) }
        }

        get("/admin/screen/{slideSet}/new/qrcodeslide") {
            call.respondEither {
                renderNewSlideEdit(call.parameterString("slideSet"), QrCodeSlide.Empty).bind()
            }
        }

        post("/admin/screen/{slideSet}/new/qrcodeslide") {
            call.createSlide<QrCodeSlide>(app) { s, e -> renderNewSlideEdit(s, QrCodeSlide.Empty, e) }
        }

        // Image-slides picker: one page, many slides at once. The GET renders the
        // unused-images list plus an upload form; the main POST creates one visible
        // ImageSlide per checked asset; the upload sub-route writes new asset files
        // (mirroring AdminAssetsRouting.kt) and redirects back to the picker.
        get("/admin/screen/{slideSet}/new/imageslide") {
            call.respondEither {
                renderImageSlidesPicker(call.parameterString("slideSet")).bind()
            }
        }

        post("/admin/screen/{slideSet}/new/imageslide") {
            val slideSetName = call.parameterString("slideSet").getOrNull() ?: return@post
            val params = call.receiveParameters()
            val picks = (params.getAll("assetImage") ?: emptyList()).filter { it.isNotBlank() }
            picks.forEach { assetImage ->
                app.screen.addSlide(slideSetName, ImageSlide(assetImage), makeVisible = true)
            }
            call.respondRedirect("/admin/screen/$slideSetName")
        }

        post("/admin/screen/{slideSet}/new/imageslide/upload") {
            val slideSetName = call.parameterString("slideSet").getOrNull() ?: return@post
            val (_, files) = call.receiveMultipart(app.config.maxFileUploadSize).collect()
            val uploadedFiles = (files["files"] ?: emptyList()).filter { it.isDefined }

            if (uploadedFiles.isEmpty()) {
                call.respondEither {
                    renderImageSlidesPicker(slideSetName.right(), uploadError = "No files selected").bind()
                }
                return@post
            }

            val errors = uploadedFiles.mapNotNull { file ->
                app.assets.write(file).fold({ "${file.name}: ${it.message}" }, { null })
            }

            if (errors.isNotEmpty()) {
                call.respondEither {
                    renderImageSlidesPicker(slideSetName.right(), uploadError = errors.joinToString("; ")).bind()
                }
            } else {
                call.respondRedirect("/admin/screen/$slideSetName/new/imageslide")
            }
        }

        get("/admin/screen/{slideSet}/{slideId}") {
            call.respondEither {
                renderSlideEdit(
                    call.parameterString("slideSet"),
                    call.parameterUUID("slideId"),
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
                val slideId = call.parameterUUID("slideId").bind()
                app.screen.showStoredSlide(slideId).bind()
            }
        }

        put("/admin/screen/{id}/setVisible/{state}") {
            call.switchApiUuid { id, visible -> app.screen.setVisible(id, visible) }
        }

        put("/admin/screen/{id}/showOnInfo/{state}") {
            call.switchApiUuid { id, show -> app.screen.showOnInfo(id, show) }
        }

        post("/admin/screen/{slideSet}/runOrder") {
            val newOrder = call.receive<List<String>>()
            call.apiRespond {
                call.parameterString("slideSet").bind()
                call.userSession(app).bind()
                newOrder
                    .mapIndexed { index, slideId -> app.screen.setRunOrder(UUID.fromString(slideId), index) }
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
                    app.screen.showStoredSlide(slideSet.first().id).bind()
                }
            }
        }

        post("/admin/screen/{slideSet}/presentation/next") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideSetName = call.parameterString("slideSet").bind()
                val (state) = app.screen.currentState()
                if (state.slideSet == slideSetName) {
                    app.screen.stopSlideSet()
                    app.screen.showNext()
                }
            }
        }

        delete("/admin/screen/slideset/{id}") {
            call.apiRespond {
                call.userSession(app).bind()
                val id = call.parameterString("id").bind()
                app.screen.deleteSlideSet(id).bind()
            }
        }

        delete("/admin/screen/{slideId}") {
            call.apiRespond {
                call.userSession(app).bind()
                val slideId = call.parameterUUID("slideId").bind()
                app.screen.delete(slideId).bind()
            }
        }
    }
}

// Process a "create slide" form submission: persist a new slide of type T in the
// given slide set (visible by default, matching the new dropdown flow) and redirect
// back to the slide-set page. On validation errors, re-render the new-slide form.
suspend inline fun <reified T> ApplicationCall.createSlide(
    app: AppServices,
    crossinline onError: suspend (AppResult<String>, AppError?) -> AppResult<Renderable>
) where
        T : Slide<T>,
        T : Validateable<T> {
    processForm<T>(
        { slide ->
            val slideSetName = parameterString("slideSet").bind()
            app.screen.addSlide(slideSetName, slide, makeVisible = true).bind()
            Redirection("/admin/screen/$slideSetName")
        },
        { form ->
            onError(parameterString("slideSet"), form.error).bind()
        }
    )
}

suspend inline fun <reified T> ApplicationCall.updateSlide(
    app: AppServices,
    crossinline onError: suspend (AppResult<String>, AppResult<UUID>, AppError?) -> AppResult<Renderable>
) where
        T : Slide<T>,
        T : Validateable<T> {
    processForm<T>(
        { slide ->
            val id = parameterUUID("slideId").bind()
            val slideSetName = parameterString("slideSet").bind()
            app.screen.update(id, slide).bind()
            Redirection("/admin/screen/$slideSetName")
        },
        { form ->
            onError(
                parameterString("slideSet"),
                parameterUUID("slideId"),
                form.error,
            ).bind()
        }
    )
}
