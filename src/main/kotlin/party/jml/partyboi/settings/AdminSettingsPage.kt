package party.jml.partyboi.settings

import kotlinx.html.*
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page

object AdminSettingsPage {
    fun render(settings: Form<PartyboiSettings>): Page = Page("Settings") {
        h1 { +"Settings" }
        renderForm(
            url = "/admin/settings",
            form = settings,
            options = mapOf("automaticVoteKeys" to DropdownOption.fromEnum<AutomaticVoteKeys> { it.label }),
        )
    }
}
