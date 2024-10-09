package party.jml.partyboi.admin.screen

import arrow.core.Option
import arrow.core.Some
import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
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

    fun renderCollectionForms(collection: String, currentlyRunning: Option<String>, forms: List<Form<*>>) =
        Page("Screen admin") {
            screenAdminNavigation()
            article {
                    if (currentlyRunning == Some(collection)) {
                        +"This screen collection is running currently"
                    } else {
                        postButton("Start", "/admin/screen/rotation/start")

                    }
            }
            forms.forEach {
                article {
                    fieldSet {
                        renderForm(it)
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
