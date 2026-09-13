package party.jml.partyboi.wizard

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.partytemplate.TemplateUpload
import party.jml.partyboi.settings.GeneralSettings
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader

object WizardPage {
    fun renderSettingsStep(settings: Form<GeneralSettings>, autofillTimeZone: Boolean) = Page("Setup wizard") {
        h1 { +"Welcome to Partyboi" }

        article {
            cardHeader("Setup wizard — step 1 of 3")
            p {
                +"Pick the time zone the party runs in, the party dates, and a color scheme. You can change these later under "
                em { +"Admin → Settings" }
                +"."
            }
        }

        renderForm(
            title = "General settings",
            url = "/wizard",
            form = settings,
            submitButtonLabel = "Save and continue",
            options = mapOf(
                "colorScheme" to DropdownOption.fromEnum<ColorScheme> { it.displayName },
                "timeZone" to TimeZone.availableZoneIds
                    .toList()
                    .sorted()
                    .map(DropdownOption.fromString),
            ),
        )

        if (autofillTimeZone) {
            script {
                unsafe {
                    +"""
                    (function() {
                        try {
                            var tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
                            if (!tz) return;
                            var sel = document.querySelector('select[name="timeZone"]');
                            if (!sel) return;
                            var hasOption = Array.prototype.some.call(sel.options, function(o) { return o.value === tz; });
                            if (hasOption) sel.value = tz;
                        } catch (e) {}
                    })();
                    """.trimIndent()
                }
            }
        }
    }

    fun renderImportStep(templateUpload: Form<TemplateUpload>) = Page("Setup wizard") {
        h1 { +"Welcome to Partyboi" }

        article {
            cardHeader("Setup wizard — step 2 of 3")
            p {
                +"If you have a party template exported from a previous Partyboi, upload it here to "
                +"recreate the compos, general compo rules and schedule. You can also do this later under "
                em { +"Admin → Settings" }
                +"."
            }
        }

        renderForm(
            title = "Import party template",
            url = "/wizard/import",
            form = templateUpload,
            submitButtonLabel = "Upload and preview",
        )

        p {
            a(href = "/wizard/skip") { +"Skip this step" }
        }
    }
}
