package party.jml.partyboi.settings

import kotlinx.datetime.TimeZone
import kotlinx.html.h1
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Page

object AdminSettingsPage {
    fun render(settings: Form<GeneralSettings>): Page = Page("Settings") {
        h1 { +"Settings" }

        renderForm(
            url = "/admin/settings",
            form = settings,
            options = mapOf(
                "colorScheme" to DropdownOption.fromEnum<ColorScheme> { it.displayName },
                "timeZone" to TimeZone.availableZoneIds
                    .toList()
                    .sorted()
                    .map(DropdownOption.fromString),
            ),
        )
    }
}
