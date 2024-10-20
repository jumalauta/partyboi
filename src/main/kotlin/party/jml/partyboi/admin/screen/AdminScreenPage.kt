package party.jml.partyboi.admin.screen

import kotlinx.html.*
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.screen.*
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.Icon
import party.jml.partyboi.templates.components.IconSet
import party.jml.partyboi.templates.components.icon
import party.jml.partyboi.templates.components.toggleButton

object AdminScreenPage {
    fun renderAdHocForm(currentlyRunning: Boolean, form: Form<*>) =
        Page("Screen admin") {
            screenAdminNavigation()
            if (currentlyRunning) {
                article {
                    +"Ad hoc screen is being shown currently"
                }
            }
            form(
                classes = "submitForm appForm",
                method = FormMethod.post,
                action = "/admin/screen/adhoc",
                encType = FormEncType.multipartFormData
            ) {
                article {
                    header { +"Ad hoc screen" }
                    fieldSet {
                        renderForm(form)
                    }
                    footer {
                        submitInput { value = "Show" }
                    }
                }
            }
        }

    fun renderSlideSetForms(slideSet: String, screenState: ScreenState, isRunning: Boolean, slides: List<SlideEditData>) =
        Page("Screen admin") {
            screenAdminNavigation()

            section {
                fieldSet {
                    attributes.put("role", "group")

                    if (screenState.slideSet != slideSet || !isRunning) {
                        postButton("/admin/screen/${slideSet}/start") {
                            icon(Icon("play"))
                            +" Auto run"
                        }
                    }
                    if (screenState.slideSet == slideSet && isRunning) {
                        postButton("/admin/screen/${slideSet}/stop") {
                            icon(Icon("pause"))
                            +" Pause auto run"
                        }
                    }
                    a(href="/admin/screen/${slideSet}/presentation") {
                        attributes.put("role", "button")
                        icon("person-chalkboard")
                        +" Presentation mode"
                    }
                }
            }

            article {
                table {
                    thead {
                        tr {
                            th(classes = "narrow") {}
                            th(classes = "narrow") {}
                            th { +"Name" }
                            th { +"Type" }
                            th(classes = "narrow") {}
                        }
                    }
                    tbody(classes = "sortable") {
                        attributes.put("data-draggable", "tr")
                        attributes.put("data-handle", ".handle")
                        attributes.put("data-callback", "/admin/screen/${slideSet}/runOrder")
                        slides.forEach { slide ->
                            tr {
                                attributes.put("data-dragid", slide.id.toString())
                                td(classes = "handle") { icon("arrows-up-down") }
                                td {
                                    if (screenState.id == slide.id) {
                                        icon("tv")
                                    } else {
                                        postButton("/admin/screen/${slideSet}/${slide.id}/show") {
                                            attributes.put("class", "flat-button")
                                            attributes.put("data-tooltip", "Show on screen")
                                            icon("play")
                                        }
                                    }
                                }
                                td { a(href="/admin/screen/${slideSet}/${slide.id}") { +slide.getName() } }
                                td { +slide.slide.javaClass.simpleName }
                                td(classes = "align-right") {
                                    toggleButton(slide.visible, IconSet.visibility, "/admin/screen/${slide.id}/setVisible")
                                }
                            }
                        }
                    }
                }
            }

            section {
                postButton("/admin/screen/${slideSet}/text") {
                    icon(Icon("align-left"))
                    +" Add text slide"
                }
                postButton("/admin/screen/${slideSet}/qrcode") {
                    icon(Icon("qrcode"))
                    +" Add QR code"
                }
            }
            script(src = "/assets/draggable.min.js") {}
            script(src = "/assets/refreshOnSlideChange.js") {}
        }

    fun renderSlideForm(slideSet: String, slide: SlideEditData) =
        Page("Edit slide") {
            form(
                method = FormMethod.post,
                action = "/admin/screen/${slideSet}/${slide.id}/${slide.slide.javaClass.simpleName.lowercase()}",
                encType = FormEncType.multipartFormData
            ) {
                fieldSet {
                    renderForm(slide.slide.getForm())
                }
                footer {
                    submitInput { value = "Save changes" }
                }
            }
        }
}

fun FlowContent.screenAdminNavigation() {
    article {
        nav {
            ul {
                li { a(href="/admin/screen/adhoc") {
                    icon("bolt-lightning")
                    +" Ad hoc"
                } }
                li { a(href="/admin/screen/rotation") {
                    icon("circle-info")
                    +" Rotation"
                } }
            }
            ul {
                li { a(href="/screen", target = "_blank") {
                    attributes.put("data-tooltip", "Show current screen")
                    icon("tv")
                } }
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

data class SlideEditData(
    val id: Int,
    val visible: Boolean,
    val slide: Slide<*>,
) {
    fun getName(): String =
        slide.getName().nonEmptyString() ?: "Untitled slide #${id}"

    companion object {
        val fromRow: (ScreenRow) -> SlideEditData = { row ->
            SlideEditData(
                id = row.id,
                visible = row.visible,
                slide = row.getSlide(),
            )
        }
    }
}
