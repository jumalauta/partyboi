package party.jml.partyboi.settings

import kotlinx.datetime.TimeZone
import kotlinx.html.article
import kotlinx.html.h1
import kotlinx.html.p
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.partytemplate.TemplateUpload
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonLink
import party.jml.partyboi.templates.components.cardHeader

object AdminSettingsPage {
    fun render(
        settings: Form<GeneralSettings>,
        templateUpload: Form<TemplateUpload> = Form.of(TemplateUpload.Empty),
    ): Page = Page("Settings") {
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

        article {
            cardHeader("Party template")
            p {
                +"Export the compos, general compo rules and schedule as a template file that can be "
                +"imported into next year's Partyboi."
            }
            buttonLink("/admin/settings/export") { +"Export party template…" }
        }

        renderForm(
            url = "/admin/settings/import",
            form = templateUpload,
            title = "Import party template",
            submitButtonLabel = "Upload and preview",
        )
    }
}
