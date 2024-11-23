package party.jml.partyboi.admin.settings

import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.StringProperty
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.signals.Signal

class SettingsService(val app: AppServices) {
    val resultsFileHeader = StringProperty(app.properties, RESULTS_FILE_HEADER)

    fun getSettings() = either {
        PartyboiSettings(
            resultsFileHeader = resultsFileHeader.get().bind(),
        )
    }

    suspend fun saveSettings(settings: PartyboiSettings) = either {
        listOf(
            resultsFileHeader.set(settings.resultsFileHeader.trimEnd()),
        ).bindAll()
        app.signals.emit(Signal.settingsUpdated())
    }

    companion object {
        const val RESULTS_FILE_HEADER = "Settings.resultsFileHeader"
    }
}

data class PartyboiSettings(
    @property:Field(order = 1, label = "results.txt header", presentation = FieldPresentation.large)
    val resultsFileHeader: String
) : Validateable<PartyboiSettings>