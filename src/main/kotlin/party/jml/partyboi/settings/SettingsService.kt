package party.jml.partyboi.settings

import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.MappedProperty
import party.jml.partyboi.data.StringProperty
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.signals.Signal

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

    fun getSettings() = either {
        PartyboiSettings(
            automaticVoteKeys = automaticVoteKeys.get().bind(),
            resultsFileHeader = resultsFileHeader.get().bind(),
        )
    }

    suspend fun saveSettings(settings: PartyboiSettings) = either {
        listOf(
            automaticVoteKeys.set(settings.automaticVoteKeys),
            resultsFileHeader.set(settings.resultsFileHeader.trimEnd()),
        ).bindAll()
        app.signals.emit(Signal.settingsUpdated())
    }

    companion object {
        const val AUTOMATIC_VOTE_KEYS = "Settings.automaticVoteKeys"
        const val RESULTS_FILE_HEADER = "Settings.resultsFileHeader"
    }
}

data class PartyboiSettings(
    @property:Field(order = 1, label = "Automatic vote keys")
    val automaticVoteKeys: AutomaticVoteKeys,
    @property:Field(order = 2, label = "results.txt header", presentation = FieldPresentation.monospace)
    val resultsFileHeader: String,
) : Validateable<PartyboiSettings>

enum class AutomaticVoteKeys(val label: String) {
    DISABLED("Disabled"),
    PER_USER("Per user"),
    PER_IP_ADDRESS("Per IP address"),
}