package party.jml.partyboi.wizard

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.Form
import party.jml.partyboi.partytemplate.ImportSelection
import party.jml.partyboi.partytemplate.admin.PartyTemplatePages
import party.jml.partyboi.partytemplate.admin.importError
import party.jml.partyboi.partytemplate.admin.renderTemplateSelection
import party.jml.partyboi.partytemplate.admin.runTemplateImport
import party.jml.partyboi.partytemplate.admin.withNonFieldErrorsVisible
import party.jml.partyboi.partytemplate.parsePartyTemplate
import party.jml.partyboi.settings.GeneralSettings
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither

fun Application.configureWizardRouting(app: AppServices) {
    suspend fun emptyImportForm(): AppResult<Form<WizardImport>> = either {
        Form.of(
            WizardImport(
                partyStartDate = app.settings.partyStartDate.get().bind() ?: app.time.today(),
                file = FileUpload.Empty,
            )
        )
    }

    suspend fun renderImportStep(form: Form<WizardImport>? = null): AppResult<Page> = either {
        WizardPage.renderImportStep(form ?: emptyImportForm().bind())
    }

    suspend fun renderSettingsStep(form: Form<GeneralSettings>? = null): AppResult<Page> = either {
        WizardPage.renderSettingsStep(
            settings = form ?: Form(
                GeneralSettings::class,
                app.settings.getGeneralSettings().bind(),
                initial = false
            ),
            // Only autofill the browser's timezone while none has ever been stored:
            // a timezone from a template import (or an earlier save) must not be
            // silently overridden, no matter which path led here.
            autofillTimeZone = form == null && !app.time.isTimeZoneStored().bind(),
        )
    }

    adminRouting {
        get("/wizard") {
            call.respondEither { renderImportStep().bind() }
        }

        post("/wizard/import") {
            call.processForm<WizardImport>(
                { form ->
                    // Parse before persisting anything, so a failed upload does not
                    // mutate stored settings.
                    val json = form.file.tempFile.readText()
                    parsePartyTemplate(json).bind()
                    // The start date must be stored before the template is analyzed:
                    // the schedule preview is computed against it.
                    app.settings.partyStartDate.set(form.partyStartDate).bind()
                    renderTemplateSelection(app, json, "/wizard/import/confirm")
                },
                { renderImportStep(it.withNonFieldErrorsVisible()).bind() },
                maxUploadSize = app.config.maxFileUploadSize,
            )
        }

        post("/wizard/import/confirm") {
            call.processForm<ImportSelection>(
                { selection ->
                    val report = runTemplateImport(app, selection)
                    PartyTemplatePages.renderImportReportPage(report, "/wizard/settings")
                },
                { formWithErrors ->
                    renderImportStep(emptyImportForm().bind().with(formWithErrors.importError())).bind()
                },
                maxUploadSize = app.config.maxFileUploadSize,
            )
        }

        get("/wizard/skip") {
            call.respondEither { Redirection("/wizard/settings") }
        }

        get("/wizard/settings") {
            call.respondEither { renderSettingsStep().bind() }
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
