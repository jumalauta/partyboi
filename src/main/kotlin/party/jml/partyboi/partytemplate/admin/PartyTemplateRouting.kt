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
import party.jml.partyboi.partytemplate.ImportSelection
import party.jml.partyboi.partytemplate.PartyTemplate
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

fun Application.configurePartyTemplateRouting(app: AppServices) {
    suspend fun renderExportPage(): AppResult<Page> = either {
        PartyTemplatePages.renderExportPage(
            generalRules = app.compos.generalRules.get().bind().rules,
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

    suspend fun Raise<AppError>.renderSelection(template: PartyTemplate, json: String, confirmUrl: String): Page =
        PartyTemplatePages.renderImportSelectionPage(
            preview = app.partyTemplates.analyze(template).bind(),
            payload = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)),
            confirmUrl = confirmUrl,
            timeZone = app.time.timeZone(),
        )

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
                    val json = upload.file.tempFile.readText()
                    val template = parsePartyTemplate(json).bind()
                    renderSelection(template, json, "/admin/settings/import/confirm")
                },
                { renderSettingsPage(it).bind() }
            )
        }

        post("/admin/settings/import/confirm") {
            call.processForm<ImportSelection>(
                { selection ->
                    val template = parsePartyTemplate(decodePayload(selection.payload).bind()).bind()
                    val report = app.partyTemplates.import(template, selection).bind()
                    PartyTemplatePages.renderImportReportPage(report, "/admin/settings")
                },
                { formWithErrors ->
                    // The selection form was rendered by us, so errors here mean a stale or
                    // tampered payload; surface them on the settings page's upload form.
                    val messages = formWithErrors.accumulatedValidationErrors.joinToString { it.message }
                    val error = formWithErrors.error ?: FormError(messages.ifEmpty { "Import failed" })
                    renderSettingsPage(Form.of(TemplateUpload.Empty).with(error)).bind()
                }
            )
        }
    }
}

fun decodePayload(payload: String): AppResult<String> =
    either {
        try {
            String(Base64.getDecoder().decode(payload), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            raise(ValidationError("payload", "The import selection has expired or is invalid", ""))
        }
    }
