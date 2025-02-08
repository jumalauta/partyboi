package party.jml.partyboi.settings

import kotlinx.html.*
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.templates.Page

object AdminSettingsPage {
    fun render(settings: Form<PartyboiSettings>): Page = Page("Settings") {
        h1 { +"Settings" }
        dataForm("/admin/settings") {
            article {
                fieldSet {
                    renderFields(
                        settings, mapOf(
                        "automaticVoteKeys" to DropdownOption.fromEnum<AutomaticVoteKeys> { it.label }
                    ))
                }
                footer {
                    submitInput { value = "Save" }
                }
            }
        }
    }
}
