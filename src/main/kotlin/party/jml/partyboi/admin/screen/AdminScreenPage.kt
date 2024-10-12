package party.jml.partyboi.admin.screen

import arrow.core.Option
import arrow.core.Some
import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.screen.Screen
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

    fun renderCollectionForms(collection: String, currentlyRunning: Option<String>, screens: List<ScreenEditData>) =
        Page("Screen admin") {
            screenAdminNavigation()
            article {
                    if (currentlyRunning == Some(collection)) {
                        postButton( "/admin/screen/rotation/stop") {
                            icon(Icon("pause"))
                            +" Pause"
                        }
                    } else {
                        postButton( "/admin/screen/rotation/start") {
                            icon(Icon("play"))
                            +" Start"
                        }
                    }
            }
            screens.forEach {
                article {
                    details {
                        summary {
                            header {
                                span { +it.screen.getName() }
                                toggleButton(it.enabled, IconSet.visibility, "/admin/screen/${it.id}/setVisible")
                            }
                        }
                        form(
                            method = FormMethod.post,
                            action = "/admin/screen/${collection}/${it.id}/${it.screen.javaClass.simpleName.lowercase()}",
                            encType = FormEncType.multipartFormData
                        ) {
                            fieldSet {
                                renderForm(it.screen.getForm())
                            }
                            footer {
                                submitInput { value = "Save changes" }
                            }
                        }
                    }
                }
            }
            article {
                postButton( "/admin/screen/rotation/text") {
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

data class ScreenEditData(
    val id: Int,
    val enabled: Boolean,
    val screen: Screen<*>,
) {
    companion object {
        val fromRow: (ScreenRow) -> ScreenEditData = { row ->
            ScreenEditData(
                id = row.id,
                enabled = row.enabled,
                screen = row.getScreen(),
            )
        }
    }
}
