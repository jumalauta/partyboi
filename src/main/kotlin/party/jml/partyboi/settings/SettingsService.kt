package party.jml.partyboi.settings

import arrow.core.getOrElse
import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.MappedProperty
import party.jml.partyboi.data.StringProperty
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Theme

class SettingsService(val app: AppServices) {
    val automaticVoteKeys = MappedProperty.enum(
        app.properties,
        AUTOMATIC_VOTE_KEYS,
        AutomaticVoteKeys.DISABLED,
    )

    val resultsFileHeader = StringProperty(
        app.properties,
        RESULTS_FILE_HEADER
    )

    val colorScheme = MappedProperty.enum(
        app.properties,
        COLOR_SCHEME,
        ColorScheme.Blue
    )

    fun getSettings() = either {
        PartyboiSettings(
            automaticVoteKeys = automaticVoteKeys.get().bind(),
            resultsFileHeader = resultsFileHeader.get().bind(),
            colorScheme = colorScheme.get().bind(),
        )
    }

    fun getTheme() = either {
        Theme(
            colorScheme = colorScheme.get().bind(),
        )
    }.getOrElse { Theme.Default }

    suspend fun saveSettings(settings: PartyboiSettings) = either {
        listOf(
            automaticVoteKeys.set(settings.automaticVoteKeys),
            resultsFileHeader.set(settings.resultsFileHeader.trimEnd()),
            colorScheme.set(settings.colorScheme),
        ).bindAll()
        app.signals.emit(Signal.settingsUpdated())
    }

    companion object {
        const val AUTOMATIC_VOTE_KEYS = "Settings.automaticVoteKeys"
        const val RESULTS_FILE_HEADER = "Settings.resultsFileHeader"
        const val COLOR_SCHEME = "Settings.colorScheme"
    }
}

data class PartyboiSettings(
    @property:Field(order = 1, label = "Automatic vote keys")
    val automaticVoteKeys: AutomaticVoteKeys,
    @property:Field(order = 3, label = "results.txt header", presentation = FieldPresentation.monospace)
    val resultsFileHeader: String,
    @property:Field(order = 2, label = "Color scheme")
    val colorScheme: ColorScheme,
) : Validateable<PartyboiSettings>

enum class AutomaticVoteKeys(val label: String) {
    DISABLED("Disabled"),
    PER_USER("Per user"),
    PER_IP_ADDRESS("Per IP address"),
}