package party.jml.partyboi.settings

import arrow.core.raise.either
import kotlinx.datetime.TimeZone
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.StoredProperties
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Theme

class SettingsService(app: AppServices) : StoredProperties(app) {
    val automaticVoteKeys = property("automaticVoteKeys", AutomaticVoteKeys.DISABLED)
    val voteKeyEmailList = property("voteKeyEmailList", emptyList<String>())
    val verifiedEmailsOnly = property("verifiedEmailsOnly", true)
    val resultsFileHeader = property("resultsFileHeader", "")
    val colorScheme = property("colorScheme", ColorScheme.Blue)

    suspend fun getGeneralSettings() = either {
        GeneralSettings(
            resultsFileHeader = resultsFileHeader.get().bind(),
            colorScheme = colorScheme.get().bind(),
            timeZone = app.time.timeZone.get().bind()
        )
    }

    suspend fun getVoteSettings() = either {
        VoteSettings(
            automaticVoteKeys = automaticVoteKeys.get().bind(),
            listOfEmails = voteKeyEmailList.get().bind().joinToString("\n"),
            verifiedEmailsOnly = verifiedEmailsOnly.get().bind()
        )
    }

    suspend fun getTheme() = either {
        Theme(
            colorScheme = colorScheme.get().bind(),
        )
    }

    suspend fun saveSettings(settings: GeneralSettings) = either {
        listOf(
            resultsFileHeader.set(settings.resultsFileHeader.trimEnd()),
            colorScheme.set(settings.colorScheme),
            app.time.timeZone.set(settings.timeZone),
        ).bindAll()
    }

    suspend fun saveSettings(settings: VoteSettings) = either {
        if (settings.automaticVoteKeys == AutomaticVoteKeys.PER_EMAIL && !app.email.isConfigured()) {
            raise(
                ValidationError(
                    "automaticVoteKeys",
                    "This option cannot be selected because email service has not been configured",
                    ""
                )
            )
        }

        listOf(
            automaticVoteKeys.set(settings.automaticVoteKeys),
            voteKeyEmailList.set(
                settings.listOfEmails
                    .split(Regex("\\s+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            ),
            verifiedEmailsOnly.set(settings.verifiedEmailsOnly),
        ).bindAll()
    }
}

data class GeneralSettings(
    @property:Field(order = 3, label = "results.txt header", presentation = FieldPresentation.monospace)
    val resultsFileHeader: String,
    @property:Field(order = 2, label = "Color scheme")
    val colorScheme: ColorScheme,
    @property:Field(order = 4, label = "Time zone")
    val timeZone: TimeZone,
) : Validateable<GeneralSettings>

data class VoteSettings(
    @property:Field(order = 1, label = "Automatic vote keys")
    val automaticVoteKeys: AutomaticVoteKeys,
    @property:Field(order = 2, label = "Email list", presentation = FieldPresentation.large)
    val listOfEmails: String,
    @property:Field(order = 3, label = "Accept only verified email addresses")
    val verifiedEmailsOnly: Boolean,
) : Validateable<VoteSettings>

enum class AutomaticVoteKeys(val label: String) {
    DISABLED("Vote keys only"),
    PER_USER("Every new user gets voting rights automatically"),
    PER_IP_ADDRESS("Every new user from distinct IP address gets voting rights automatically"),
    PER_EMAIL("Voting rights are granted according to an email list"),
}