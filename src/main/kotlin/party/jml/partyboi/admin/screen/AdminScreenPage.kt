package party.jml.partyboi.admin.screen

import party.jml.partyboi.templates.Page
import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.screen.AdHocScreen

object AdminScreenPage {
    fun render(form: Form<AdHocScreen>) =
        Page("Screen admin") {
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
}
