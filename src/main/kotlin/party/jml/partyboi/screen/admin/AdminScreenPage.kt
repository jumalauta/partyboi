package party.jml.partyboi.screen.admin

import kotlinx.html.*
import party.jml.partyboi.assets.Asset
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.renderReadonlyFields
import party.jml.partyboi.screen.*
import party.jml.partyboi.screen.slides.Slide
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.triggers.TriggerRow
import java.util.*

object AdminScreenPage {
    fun renderAdHocForm(
        form: Form<*>,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Screen admin",
        subLinks = slideSets.map { it.toNavItem() },
    ) {
        renderWithScreenMonitoring(false) {
            renderForm(
                url = "/admin/screen/adhoc",
                form = form,
                title = "Ad hoc screen",
                submitButtonLabel = "Show now"
            )
        }
    }

    fun renderSlideSetForms(
        slideSet: String,
        screenState: ScreenState,
        isRunning: Boolean,
        slides: List<SlideEditData>,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Screen admin",
        subLinks = slideSets.map { it.toNavItem() },
    ) {
        val willHaltAutoRun = slides.find {
            it.slide is AutoRunHalting && it.slide.haltAutoRun()
        } != null

        h1 { +(slideSets.find { it.id == slideSet }?.name ?: "Slide set $slideSet") }

        renderWithScreenMonitoring(true) {
            reloadSection {
                nav {
                    ul {
                        if (!willHaltAutoRun) {
                            if (screenState.slideSet != slideSet || !isRunning) {
                                li {
                                    val isDisabled = slides.all { !it.visible }
                                    if (isDisabled) {
                                        tooltip("Need at least one slide set visible to activate autorun")
                                    }
                                    postButton("/admin/screen/${slideSet}/start") {
                                        disabled = isDisabled
                                        icon(Icon("play"))
                                        +" Auto run"
                                    }
                                }
                            }
                            if (screenState.slideSet == slideSet && isRunning) {
                                li {
                                    postButton("/admin/screen/${slideSet}/stop") {
                                        icon(Icon("pause"))
                                        +" Pause auto run"
                                    }
                                }
                            }
                        } else {
                            li {
                                postButton("/admin/screen/${slideSet}/next") {
                                    icon(Icon.next)
                                    +" Show next slide"
                                }
                            }
                        }
                    }
                }
                article {
                    table {
                        tbody(classes = "sortable") {
                            attributes.put("data-draggable", "tr")
                            attributes.put("data-handle", ".handle")
                            attributes.put("data-callback", "/admin/screen/${slideSet}/runOrder")
                            slides.forEach { slide ->
                                tr {
                                    attributes.put("data-dragid", slide.id.toString())
                                    if (!slide.readOnly) {
                                        td(classes = "handle tight") { icon("arrows-up-down") }
                                    } else {
                                        td(classes = "tight") {}
                                    }
                                    td(classes = "tight") {
                                        if (screenState.id == slide.id) {
                                            if (isRunning) {
                                                span { attributes["aria-busy"] = "true" }
                                            } else {
                                                icon("tv")
                                            }
                                        } else {
                                            postButton("/admin/screen/${slideSet}/${slide.id}/show") {
                                                attributes.put("class", "flat-button")
                                                tooltip("Show on screen")
                                                icon("play")
                                            }
                                        }
                                    }
                                    td(classes = "wide") {
                                        if (slide.slide is NonEditable) {
                                            tooltip("This slide cannot be edited")
                                            val type = slide.slide.getType()
                                            icon(type.icon, type.description)
                                            +" "
                                            +slide.getName()
                                        } else {
                                            a(href = "/admin/screen/${slideSet}/${slide.id}") {
                                                val type = slide.slide.getType()
                                                icon(type.icon, type.description)
                                                +" "
                                                +slide.getName()
                                            }
                                        }
                                    }
                                    td(classes = "settings") {
                                        if (!slide.readOnly) {
                                            toggleButton(
                                                slide.visible,
                                                IconSet.visibility,
                                                "/admin/screen/${slide.id}/setVisible"
                                            )
                                            toggleButton(
                                                slide.showOnInfoPage,
                                                IconSet.showOnInfoPage,
                                                "/admin/screen/${slide.id}/showOnInfo"
                                            )
                                            deleteButton(
                                                url = "/admin/screen/${slide.id}",
                                                tooltipText = "Delete ${slide.getName()}",
                                                confirmation = "Do you really want to delete slide ${slide.getName()}?"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            details(classes = "dropdown") {
                summary { +"Add slide" }
                ul {
                    li {
                        flatPostButton("/admin/screen/${slideSet}/text") {
                            icon(Icon("list-ul"))
                            +" Normal text slide"
                        }
                    }
                    li {
                        flatPostButton("/admin/screen/${slideSet}/qrcode") {
                            icon(Icon("qrcode"))
                            +" Slide with a QR code"
                        }
                    }
                    li {
                        flatPostButton("/admin/screen/${slideSet}/image") {
                            icon(Icon("image"))
                            +" Full screen image"
                        }
                    }
                    li {
                        flatPostButton("/admin/screen/${slideSet}/schedule") {
                            icon(Icon("calendar"))
                            +" All missing schedule slides"
                        }
                    }
                }
            }
        }

        script(src = "/assets/draggable.min.js") {}
    }

    private fun FlowContent.renderWithScreenMonitoring(refreshOnSlideChange: Boolean, block: FlowContent.() -> Unit) {
        columns({
            block()
        }, {
            div(classes = "screen-preview-container") {
                iframe(classes = "screen-preview") {
                    attributes.put("src", "/screen")
                    attributes.put("width", "1920")
                    attributes.put("height", "1080")
                }
            }
            a(href = "/screen", target = "_blank") {
                +"Open in new tab"
            }
        })
        if (refreshOnSlideChange) {
            script(src = "/assets/refreshOnSlideChange.js") {}
        }
    }

    fun renderSlideForm(
        slideSet: String,
        slide: SlideEditData,
        triggers: List<TriggerRow>,
        assetImages: List<Asset>,
        errors: AppError? = null,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Edit slide",
        subLinks = slideSets.map { it.toNavItem() },
    ) {
        h1 { +"${slide.getName()} / ${slideSets.find { it.id == slideSet }?.name ?: "Slide set $slideSet"}" }
        val form = slide.slide.getForm()
        if (slide.readOnly) {
            article { renderReadonlyFields(form) }
        } else {
            renderForm(
                url = "/admin/screen/${slideSet}/${slide.id}/${slide.slide.javaClass.simpleName.lowercase()}",
                form = if (errors == null) form else form.with(errors),
                submitButtonLabel = "Save changes",
                options = mapOf("assetImage" to assetImages.map(DropdownOption.fromAsset))
            )
        }

        if (triggers.isNotEmpty()) {
            article {
                header { +"When this slide is shown the following actions are executed automatically" }
                ul { triggers.forEach { li { +it.description } } }
            }
        }
    }
}

fun FlowContent.postButton(url: String, block: BUTTON.() -> Unit) {
    button {
        onClick = Javascript.build {
            httpPost(url)
            refresh()
        }
        block()
    }
}

fun FlowContent.flatPostButton(url: String, block: BUTTON.() -> Unit) {
    button(classes = "flat-button") {
        onClick = Javascript.build {
            httpPost(url)
            refresh()
        }
        block()
    }
}

data class SlideEditData(
    val id: UUID,
    val visible: Boolean,
    val showOnInfoPage: Boolean,
    val slide: Slide<*>,
    val readOnly: Boolean,
) {
    fun getName(): String =
        slide.getName().nonEmptyString() ?: "New slide"

    companion object {
        val fromRow: (ScreenRow) -> SlideEditData = { row ->
            SlideEditData(
                id = row.id,
                visible = row.visible,
                showOnInfoPage = row.showOnInfoPage,
                slide = row.getSlide(),
                readOnly = row.readOnly,
            )
        }
    }
}
