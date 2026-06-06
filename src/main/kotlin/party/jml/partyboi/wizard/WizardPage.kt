package party.jml.partyboi.wizard

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.settings.GeneralSettings
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader

object WizardPage {
    fun renderSettingsStep(settings: Form<GeneralSettings>, autofillTimeZone: Boolean) = Page("Setup wizard") {
        h1 { +"Welcome to Partyboi" }

        article {
            cardHeader("Setup wizard — step 1 of 2")
            p {
                +"Pick the time zone the party runs in and a color scheme. You can change these later under "
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
}
