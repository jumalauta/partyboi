package party.jml.partyboi.wizard

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.settings.GeneralSettings
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither

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
                    app.settings.wizardCompleted.set(true).bind()
                    Redirection("/admin/voting")
                },
                { renderStep1(it).bind() }
            )
        }
    }
}
