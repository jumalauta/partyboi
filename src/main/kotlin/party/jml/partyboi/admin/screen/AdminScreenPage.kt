package party.jml.partyboi.admin.screen

import arrow.core.Option
import arrow.core.Some
import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.screen.Slide
import party.jml.partyboi.screen.ScreenRow
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

    fun renderSlideSetForms(slideSet: String, currentlyRunning: Option<String>, slides: List<SlideEditData>) =
        Page("Screen admin") {
            screenAdminNavigation()

            section {
                if (currentlyRunning == Some(slideSet)) {
                    postButton("/admin/screen/rotation/stop") {
                        icon(Icon("pause"))
                        +" Pause"
                    }
                } else {
                    postButton("/admin/screen/rotation/start") {
                        icon(Icon("play"))
                        +" Start"
                    }
                }
            }

            slides.forEach {
                article {
                    details {
                        summary {
                            header {
                                span { +it.slide.getName() }
                                toggleButton(it.visible, IconSet.visibility, "/admin/screen/${it.id}/setVisible")
                            }
                        }
                        form(
                            method = FormMethod.post,
                            action = "/admin/screen/${slideSet}/${it.id}/${it.slide.javaClass.simpleName.lowercase()}",
                            encType = FormEncType.multipartFormData
                        ) {
                            fieldSet {
                                renderForm(it.slide.getForm())
                            }
                            footer {
                                submitInput { value = "Save changes" }
                            }
                        }
                    }
                }
            }

            section {
                postButton("/admin/screen/rotation/text") {
                    icon(Icon("align-left"))
                    +" Add text screen"
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
    companion object {
        val fromRow: (ScreenRow) -> SlideEditData = { row ->
            SlideEditData(
                id = row.id,
                visible = row.visible,
                slide = row.getScreen(),
            )
        }
    }
}
