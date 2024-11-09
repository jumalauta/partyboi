package party.jml.partyboi.admin.screen

import kotlinx.html.*
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.screen.*
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.templates.navigationDropdown
import party.jml.partyboi.triggers.TriggerRow

object AdminScreenPage {
    fun renderAdHocForm(
        form: Form<*>,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Screen admin",
        subLinks = slideSets.map { it.toNavItem() },
    ) {
        renderWithScreenMonitoring {
            dataForm("/admin/screen/adhoc") {
                article {
                    header { +"Ad hoc screen" }
                    fieldSet {
                        renderFields(form)
                    }
                    footer {
                        submitInput { value = "Show now" }
                    }
                }
            }
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
        h1 { +(slideSets.find { it.id == slideSet }?.name ?: "Slide set $slideSet") }

        nav {
            ul {
                if (screenState.slideSet != slideSet || !isRunning) {
                    li {
                        postButton("/admin/screen/${slideSet}/start") {
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
            }
        }

        renderWithScreenMonitoring {
            article {
                table {
                    tbody(classes = "sortable") {
                        attributes.put("data-draggable", "tr")
                        attributes.put("data-handle", ".handle")
                        attributes.put("data-callback", "/admin/screen/${slideSet}/runOrder")
                        slides.forEach { slide ->
                            tr {
                                attributes.put("data-dragid", slide.id.toString())
                                td(classes = "handle tight") { icon("arrows-up-down") }
                                td(classes = "tight") {
                                    if (screenState.id == slide.id) {
                                        icon("tv")
                                    } else {
                                        postButton("/admin/screen/${slideSet}/${slide.id}/show") {
                                            attributes.put("class", "flat-button")
                                            tooltip("Show on screen")
                                            icon("play")
                                        }
                                    }
                                }
                                td(classes = "wide") {
                                    a(href = "/admin/screen/${slideSet}/${slide.id}") {
                                        val type = slide.slide.getType()
                                        icon(type.icon, type.description)
                                        +" "
                                        +slide.getName()
                                    }
                                }
                                td(classes = "settings") {
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
                details(classes = "dropdown") {
                    summary { +"Add slide" }
                    ul {
                        li {
                            flatPostButton("/admin/screen/${slideSet}/text") {
                                icon(Icon("list-ul"))
                                +" Text slide"
                            }
                        }
                        li {
                            flatPostButton("/admin/screen/${slideSet}/qrcode") {
                                icon(Icon("qrcode"))
                                +" QR code"
                            }
                        }
                        li {
                            flatPostButton("/admin/screen/${slideSet}/image") {
                                icon(Icon("image"))
                                +" Image"
                            }
                        }
                    }
                }
            }
        }

        script(src = "/assets/draggable.min.js") {}
    }

    fun FlowContent.renderWithScreenMonitoring(block: FlowContent.() -> Unit) {
        columns({
            reloadSection {
                block()
            }
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
        script(src = "/assets/refreshOnSlideChange.js") {}
    }

    fun renderSlideForm(
        slideSet: String,
        slide: SlideEditData,
        triggers: List<TriggerRow>,
        assetImages: List<String>,
        errors: AppError? = null,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Edit slide",
        subLinks = slideSets.map { it.toNavItem() },
    ) {
        h1 { +"${slide.getName()} / ${slideSets.find { it.id == slideSet }?.name ?: "Slide set ${slideSet}"}" }
        val form = slide.slide.getForm()
        article {
            dataForm("/admin/screen/${slideSet}/${slide.id}/${slide.slide.javaClass.simpleName.lowercase()}") {
                fieldSet {
                    renderFields(
                        if (errors == null) form else form.with(errors),
                        mapOf("assetImage" to assetImages.map(DropdownOption.fromString))
                    )
                }
                footer {
                    submitInput { value = "Save changes" }
                }
            }
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
    val id: Int,
    val visible: Boolean,
    val showOnInfoPage: Boolean,
    val slide: Slide<*>,
) {
    fun getName(): String =
        slide.getName().nonEmptyString() ?: "Untitled slide #${id}"

    companion object {
        val fromRow: (ScreenRow) -> SlideEditData = { row ->
            SlideEditData(
                id = row.id,
                visible = row.visible,
                showOnInfoPage = row.showOnInfoPage,
                slide = row.getSlide(),
            )
        }
    }
}
