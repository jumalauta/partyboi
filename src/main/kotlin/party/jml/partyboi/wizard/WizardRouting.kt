package party.jml.partyboi.wizard

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.FormError
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.Form
import party.jml.partyboi.partytemplate.ImportSelection
import party.jml.partyboi.partytemplate.admin.PartyTemplatePages
import party.jml.partyboi.partytemplate.admin.decodePayload
import party.jml.partyboi.partytemplate.parsePartyTemplate
import party.jml.partyboi.settings.GeneralSettings
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import java.util.*

fun Application.configureWizardRouting(app: AppServices) {
    suspend fun renderImportStep(form: Form<WizardImport>? = null): AppResult<Page> = either {
        WizardPage.renderImportStep(
            form ?: Form.of(
                WizardImport(
                    partyStartDate = app.settings.partyStartDate.get().bind() ?: app.time.today(),
                    file = FileUpload.Empty,
                )
            )
        )
    }

    suspend fun renderSettingsStep(
        form: Form<GeneralSettings>? = null,
        autofillTimeZone: Boolean = false,
    ): AppResult<Page> = either {
        WizardPage.renderSettingsStep(
            settings = form ?: Form(
                GeneralSettings::class,
                app.settings.getGeneralSettings().bind(),
                initial = false
            ),
            autofillTimeZone = autofillTimeZone,
        )
    }

    adminRouting {
        get("/wizard") {
            call.respondEither { renderImportStep().bind() }
        }

        post("/wizard/import") {
            call.processForm<WizardImport>(
                { form ->
                    // The start date must be stored before the template is analyzed:
                    // the schedule preview is computed against it.
                    app.settings.partyStartDate.set(form.partyStartDate).bind()
                    val json = form.file.tempFile.readText()
                    val template = parsePartyTemplate(json).bind()
                    PartyTemplatePages.renderImportSelectionPage(
                        preview = app.partyTemplates.analyze(template).bind(),
                        payload = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)),
                        confirmUrl = "/wizard/import/confirm",
                        timeZone = app.time.timeZone(),
                    )
                },
                { renderImportStep(it).bind() }
            )
        }

        post("/wizard/import/confirm") {
            call.processForm<ImportSelection>(
                { selection ->
                    val template = parsePartyTemplate(decodePayload(selection.payload).bind()).bind()
                    val report = app.partyTemplates.import(template, selection).bind()
                    // imported=1 keeps the settings step from autofilling the browser's
                    // timezone over a value that came from the template.
                    PartyTemplatePages.renderImportReportPage(report, "/wizard/settings?imported=1")
                },
                { formWithErrors ->
                    val messages = formWithErrors.accumulatedValidationErrors.joinToString { it.message }
                    val error = formWithErrors.error ?: FormError(messages.ifEmpty { "Import failed" })
                    val form = Form.of(
                        WizardImport(
                            partyStartDate = app.settings.partyStartDate.get().bind() ?: app.time.today(),
                            file = FileUpload.Empty,
                        )
                    ).with(error)
                    renderImportStep(form).bind()
                }
            )
        }

        get("/wizard/skip") {
            call.respondEither { Redirection("/wizard/settings") }
        }

        get("/wizard/settings") {
            call.respondEither {
                renderSettingsStep(autofillTimeZone = call.parameters["imported"] == null).bind()
            }
        }

        post("/wizard/settings") {
            call.processForm<GeneralSettings>(
                {
                    app.settings.saveSettings(it).bind()
                    app.settings.wizardCompleted.set(true).bind()
                    Redirection("/admin/voting")
                },
                { renderSettingsStep(it).bind() }
            )
        }
    }
}
