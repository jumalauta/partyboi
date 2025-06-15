package party.jml.partyboi.settings

import arrow.core.raise.either
import kotlinx.datetime.TimeZone
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.StoredProperties
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Theme

class SettingsService(app: AppServices) : StoredProperties(app) {
    val automaticVoteKeys = property("automaticVoteKeys", AutomaticVoteKeys.DISABLED)
    val resultsFileHeader = property("resultsFileHeader", "")
    val colorScheme = property("colorScheme", ColorScheme.Blue)

    suspend fun getSettings() = either {
        PartyboiSettings(
            automaticVoteKeys = automaticVoteKeys.get().bind(),
            resultsFileHeader = resultsFileHeader.get().bind(),
            colorScheme = colorScheme.get().bind(),
            timeZone = app.time.timeZone.get().bind()
        )
    }

    suspend fun getTheme() = either {
        Theme(
            colorScheme = colorScheme.get().bind(),
        )
    }

    suspend fun saveSettings(settings: PartyboiSettings) = either {
        listOf(
            automaticVoteKeys.set(settings.automaticVoteKeys),
            resultsFileHeader.set(settings.resultsFileHeader.trimEnd()),
            colorScheme.set(settings.colorScheme),
            app.time.timeZone.set(settings.timeZone),
        ).bindAll()
        app.signals.emit(Signal.settingsUpdated())
    }
}

data class PartyboiSettings(
    @property:Field(order = 1, label = "Automatic vote keys")
    val automaticVoteKeys: AutomaticVoteKeys,
    @property:Field(order = 3, label = "results.txt header", presentation = FieldPresentation.monospace)
    val resultsFileHeader: String,
    @property:Field(order = 2, label = "Color scheme")
    val colorScheme: ColorScheme,
    @property:Field(order = 4, label = "Time zone")
    val timeZone: TimeZone,
) : Validateable<PartyboiSettings>

enum class AutomaticVoteKeys(val label: String) {
    DISABLED("Disabled"),
    PER_USER("Per user"),
    PER_IP_ADDRESS("Per IP address"),
}