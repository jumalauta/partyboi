package party.jml.partyboi.admin.screen

import kotlinx.html.*
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.screen.*
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.triggers.Action
import party.jml.partyboi.triggers.TriggerRow

object AdminScreenPage {
    fun renderAdHocForm(currentlyRunning: Boolean, form: Form<*>) =
        Page("Screen admin") {
            screenAdminNavigation()
            if (currentlyRunning) {
                article {
                    +"Ad hoc screen is being shown currently"
                }
            }
            dataForm("/admin/screen/adhoc") {
                article {
                    header { +"Ad hoc screen" }
                    fieldSet {
                        renderFields(form)
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
                                            tooltip("Show on screen")
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

            footer {
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
                        li {
                            a(href = "/admin/screen/${slideSet}/presentation", classes = "no-margin") {
                                attributes.put("role", "button")
                                icon("person-chalkboard")
                                +" Presentation mode"
                            }
                        }
                    }
                    ul {
                        li {
                            postButton("/admin/screen/${slideSet}/text") {
                                icon(Icon("align-left"))
                                +" Add text slide"
                            }
                        }
                        li {
                            postButton("/admin/screen/${slideSet}/qrcode") {
                                icon(Icon("qrcode"))
                                +" Add QR code"
                            }

                        }
                    }
                }
            }
            script(src = "/assets/draggable.min.js") {}
            script(src = "/assets/refreshOnSlideChange.js") {}
        }

    fun renderSlideForm(slideSet: String, slide: SlideEditData, triggers: List<TriggerRow>) =
        Page("Edit slide") {
            article {
                dataForm("/admin/screen/${slideSet}/${slide.id}/${slide.slide.javaClass.simpleName.lowercase()}") {
                    fieldSet {
                        renderFields(slide.slide.getForm())
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
                    tooltip("Show current screen")
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
