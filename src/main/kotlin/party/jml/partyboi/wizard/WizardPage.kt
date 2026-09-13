package party.jml.partyboi.wizard

import arrow.core.Option
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.Description
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Label
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.settings.GeneralSettings
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.validation.Validateable

// The wizard's template import form: the party start date is asked here because
// the imported schedule is stored relative to it.
data class WizardImport(
    @Label("Party start date")
    @Description("The imported schedule is placed on the party days starting from this date.")
    val partyStartDate: LocalDate,
    @Field(label = "Template file", description = "A party template JSON file exported from Partyboi.")
    val file: FileUpload,
) : Validateable<WizardImport> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        cond("file", file.name, !file.isDefined, "Select a template file")
    )
}

object WizardPage {
    fun renderImportStep(form: Form<WizardImport>) = Page("Setup wizard") {
        h1 { +"Welcome to Partyboi" }

        article {
            cardHeader("Setup wizard — step 1 of 2")
            p {
                +"If you have a party template exported from a previous Partyboi, upload it here to "
                +"recreate the compos, general compo rules, settings and schedule. If you don't have "
                +"one, skip this step and set everything up by hand later."
            }
        }

        renderForm(
            title = "Import party template",
            url = "/wizard/import",
            form = form,
            submitButtonLabel = "Upload and preview",
        )

        p {
            a(href = "/wizard/skip") { +"Skip this step" }
        }
    }

    fun renderSettingsStep(settings: Form<GeneralSettings>, autofillTimeZone: Boolean) = Page("Setup wizard") {
        h1 { +"Welcome to Partyboi" }

        article {
            cardHeader("Setup wizard — step 2 of 2")
            p {
                +"Check the time zone the party runs in, the party dates, and a color scheme. Values "
                +"from an imported template are already filled in. You can change these later under "
                em { +"Admin → Settings" }
                +"."
            }
        }

        renderForm(
            title = "General settings",
            url = "/wizard/settings",
            form = settings,
            submitButtonLabel = "Save and finish",
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
