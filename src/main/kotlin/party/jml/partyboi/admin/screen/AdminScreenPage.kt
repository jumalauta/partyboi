package party.jml.partyboi.admin.screen

import arrow.core.Option
import arrow.core.Some
import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.switchLink
import party.jml.partyboi.screen.Screen
import party.jml.partyboi.screen.ScreenRow
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page

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
                        +"This screen collection is running currently"
                    } else {
                        postButton("Start", "/admin/screen/rotation/start")
                    }
            }
            screens.forEach {
                article {
                    header(classes = "space-between") {
                        span { +it.screen.getName() }
                        switchLink(it.enabled, "Visible", "Hidden", "/admin/screen/${it.id}/setVisible")
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
            article {
                postButton("Add text screen", "/admin/screen/rotation/text")
            }
        }
}

fun FlowContent.screenAdminNavigation() {
    article {
        nav {
            ul {
                li { a(href="/admin/screen/adhoc") { +"Ad hoc" } }
                li { a(href="/admin/screen/rotation") { +"Rotation" } }
            }
            ul {
                li { a(href="/screen", target = "_blank") { +"Show screen" } }
            }
        }
    }
}

fun FlowContent.postButton(text: String, url: String) {
    button {
        onClick = Javascript.build {
            httpPost(url)
            refresh()
        }
        +text
    }
}

fun FlowContent.deleteButton(text: String, url: String) {
    button {
        onClick = Javascript.build {
            httpDelete(url)
            refresh()
        }
        +text
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
