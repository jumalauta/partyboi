package party.jml.partyboi.settings

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither

fun Application.configureSettingsRouting(app: AppServices) {
    suspend fun renderSettings(settingsForm: Form<GeneralSettings>? = null): AppResult<Page> = either {
        AdminSettingsPage.render(
            settings = settingsForm ?: Form(
                GeneralSettings::class,
                app.settings.getGeneralSettings().bind(),
                initial = false
            )
        )
    }

    adminRouting {
        get("/admin/settings") {
            call.respondEither({ renderSettings() })
        }

        post("/admin/settings") {
            call.processForm<GeneralSettings>(
                { app.settings.saveSettings(it).map { Redirection("/admin/settings") } },
                { renderSettings(it) }
            )
        }
    }
}