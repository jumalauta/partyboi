package party.jml.partyboi.compos.admin

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.NewManualResult
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page

object AdminEditManualResultPage {
    fun render(
        compo: Compo,
        resultId: String,
        form: Form<NewManualResult>,
    ) = Page(title = "Edit result") {
        h1 { +"${compo.displayName} — edit result" }

        dataForm("/admin/compos/${compo.id}/manual-results/$resultId") {
            article {
                fieldSet {
                    renderFields(form)
                }
                footer {
                    submitInput { value = "Save changes" }
                    a(href = "/admin/compos/${compo.id}", classes = "secondary") {
                        attributes["role"] = "button"
                        +"Cancel"
                    }
                }
            }
        }
    }
}
