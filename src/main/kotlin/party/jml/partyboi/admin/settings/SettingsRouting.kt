package party.jml.partyboi.admin.settings

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.processForm
import party.jml.partyboi.data.receiveForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither

fun Application.configureSettingsRouting(app: AppServices) {
    fun renderSettings(settingsForm: Form<PartyboiSettings>? = null): Either<AppError, Page> = either {
        AdminSettingsPage.render(
            settings = settingsForm ?: Form(PartyboiSettings::class, app.settings.getSettings().bind(), initial = false)
        )
    }

    adminRouting {
        get("/admin/settings") {
            call.respondEither({ renderSettings() })
        }

        post("/admin/settings") {
            call.processForm<PartyboiSettings>(
                { app.settings.saveSettings(it).map { Redirection("/admin/settings") } },
                { renderSettings(it) }
            )
        }
    }
}