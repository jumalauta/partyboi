package party.jml.partyboi.partytemplate.admin

import arrow.core.raise.Raise
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.User
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.FormError
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.toFilenameToken
import party.jml.partyboi.form.Form
import party.jml.partyboi.partytemplate.ExportSelection
import party.jml.partyboi.partytemplate.ImportReport
import party.jml.partyboi.partytemplate.ImportSelection
import party.jml.partyboi.partytemplate.TemplateJson
import party.jml.partyboi.partytemplate.TemplateUpload
import party.jml.partyboi.partytemplate.parsePartyTemplate
import party.jml.partyboi.settings.AdminSettingsPage
import party.jml.partyboi.settings.GeneralSettings
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.encodeToStringSafe
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.validation.Validateable
import java.util.*

// A JSON file download. respondPage appends headers() to any Renderable, so the
// attachment disposition rides along without bypassing the normal response path.
class TemplateDownload(private val json: String, private val filename: String) : Renderable {
    override fun getContentType(): ContentType = ContentType.Application.Json
    override fun getContent(user: User?, path: String): String = json
    override fun headers(): Map<String, String> = mapOf(
        HttpHeaders.ContentDisposition to ContentDisposition.Attachment
            .withParameter(ContentDisposition.Parameters.FileName, filename)
            .toString()
    )
}

// Shared import pipeline, used by both the admin settings routes here and the
// wizard's import step — only the confirm/continue URLs and error pages differ.

suspend fun Raise<AppError>.renderTemplateSelection(app: AppServices, json: String, confirmUrl: String): Page {
    val template = parsePartyTemplate(json).bind()
    return PartyTemplatePages.renderImportSelectionPage(
        preview = app.partyTemplates.analyze(template).bind(),
        payload = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)),
        confirmUrl = confirmUrl,
    )
}

suspend fun Raise<AppError>.runTemplateImport(app: AppServices, selection: ImportSelection): ImportReport {
    val template = parsePartyTemplate(decodePayload(selection.payload).bind()).bind()
    return app.partyTemplates.import(template, selection).bind()
}

// A ValidationError whose target is not a field of this form (e.g. "partyStartDate"
// raised against a TemplateUpload) would render nowhere; surface it as a form-level
// error instead of silently re-rendering the page.
fun <T : Validateable<T>> Form<T>.withNonFieldErrorsVisible(): Form<T> {
    val fieldNames = schema.properties.map { it.name }.toSet()
    val orphans = accumulatedValidationErrors.filter { it.target !in fieldNames }
    return if (error == null && orphans.isNotEmpty()) {
        with(FormError(orphans.joinToString { it.message }))
    } else this
}

// The fallback error for the confirm step, whose form was rendered by us: any
// failure there means a stale or tampered payload.
fun <T : Validateable<T>> Form<T>.importError(): AppError {
    val messages = accumulatedValidationErrors.joinToString { it.message }
    return error ?: FormError(messages.ifEmpty { "Import failed" })
}

fun decodePayload(payload: String): AppResult<String> =
    either {
        try {
            String(Base64.getDecoder().decode(payload), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            raise(ValidationError("payload", "The import selection has expired or is invalid", ""))
        }
    }

fun Application.configurePartyTemplateRouting(app: AppServices) {
    suspend fun renderExportPage(): AppResult<Page> = either {
        PartyTemplatePages.renderExportPage(
            generalRules = app.compos.generalRules.get().bind().rules,
            partyDays = app.settings.partyDays.get().bind(),
            resultsFileHeader = app.settings.resultsFileHeader.get().bind(),
            compos = app.compos.getAllCompos().bind(),
            events = app.events.getAll().bind(),
            timeZone = app.time.timeZone(),
        )
    }

    suspend fun renderSettingsPage(uploadForm: Form<TemplateUpload>): AppResult<Page> = either {
        AdminSettingsPage.render(
            settings = Form(GeneralSettings::class, app.settings.getGeneralSettings().bind(), initial = false),
            templateUpload = uploadForm,
        )
    }

    adminRouting {
        get("/admin/settings/export") {
            call.respondEither { renderExportPage().bind() }
        }

        post("/admin/settings/export") {
            call.processForm<ExportSelection>(
                { selection ->
                    val template = app.partyTemplates.buildTemplate(selection).bind()
                    val json = TemplateJson.encodeToStringSafe(template).bind()
                    val party = app.config.instanceName.toFilenameToken(true) ?: "party"
                    TemplateDownload(json, "partyboi-template-$party-${app.time.today()}.json")
                },
                { renderExportPage().bind() }
            )
        }

        post("/admin/settings/import") {
            call.processForm<TemplateUpload>(
                { upload ->
                    renderTemplateSelection(app, upload.file.tempFile.readText(), "/admin/settings/import/confirm")
                },
                { renderSettingsPage(it.withNonFieldErrorsVisible()).bind() },
                maxUploadSize = app.config.maxFileUploadSize,
            )
        }

        post("/admin/settings/import/confirm") {
            call.processForm<ImportSelection>(
                { selection ->
                    val report = runTemplateImport(app, selection)
                    PartyTemplatePages.renderImportReportPage(report, "/admin/settings")
                },
                { renderSettingsPage(Form.of(TemplateUpload.Empty).with(it.importError())).bind() },
                // The base64 payload is 4/3 the template's size, so the confirm step
                // needs the same headroom as the upload.
                maxUploadSize = app.config.maxFileUploadSize,
            )
        }
    }
}
