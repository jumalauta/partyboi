package party.jml.partyboi.wizard

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.FormError
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.partytemplate.ImportSelection
import party.jml.partyboi.partytemplate.TemplateUpload
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
    suspend fun renderStep1(form: Form<GeneralSettings>? = null): AppResult<Page> = either {
        WizardPage.renderSettingsStep(
            settings = form ?: Form(
                GeneralSettings::class,
                app.settings.getGeneralSettings().bind(),
                initial = false
            ),
            autofillTimeZone = form == null,
        )
    }

    adminRouting {
        get("/wizard") {
            call.respondEither { renderStep1().bind() }
        }

        post("/wizard") {
            call.processForm<GeneralSettings>(
                {
                    app.settings.saveSettings(it).bind()
                    // The wizard completes on the import step (or its skip link), which
                    // needs the party start date saved here to place template events.
                    Redirection("/wizard/import")
                },
                { renderStep1(it).bind() }
            )
        }

        get("/wizard/import") {
            call.respondEither { WizardPage.renderImportStep(Form.of(TemplateUpload.Empty)) }
        }

        post("/wizard/import") {
            call.processForm<TemplateUpload>(
                { upload ->
                    val json = upload.file.tempFile.readText()
                    val template = parsePartyTemplate(json).bind()
                    PartyTemplatePages.renderImportSelectionPage(
                        preview = app.partyTemplates.analyze(template).bind(),
                        payload = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)),
                        confirmUrl = "/wizard/import/confirm",
                        timeZone = app.time.timeZone(),
                    )
                },
                { WizardPage.renderImportStep(it) }
            )
        }

        post("/wizard/import/confirm") {
            call.processForm<ImportSelection>(
                { selection ->
                    val template = parsePartyTemplate(decodePayload(selection.payload).bind()).bind()
                    val report = app.partyTemplates.import(template, selection).bind()
                    app.settings.wizardCompleted.set(true).bind()
                    PartyTemplatePages.renderImportReportPage(report, "/admin/voting")
                },
                { formWithErrors ->
                    val messages = formWithErrors.accumulatedValidationErrors.joinToString { it.message }
                    val error = formWithErrors.error ?: FormError(messages.ifEmpty { "Import failed" })
                    WizardPage.renderImportStep(Form.of(TemplateUpload.Empty).with(error))
                }
            )
        }

        get("/wizard/skip") {
            call.respondEither {
                app.settings.wizardCompleted.set(true).bind()
                Redirection("/admin/voting")
            }
        }
    }
}
