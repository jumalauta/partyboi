package party.jml.partyboi.settings

import arrow.core.raise.either
import kotlinx.datetime.TimeZone
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Label
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Theme
import party.jml.partyboi.validation.Validateable
import party.jml.partyboi.voting.EmptyVoteHandling
import party.jml.partyboi.voting.PointScale
import party.jml.partyboi.voting.ScoringMethod
import party.jml.partyboi.voting.VotingSettings

class SettingsService(app: AppServices) : Service(app) {
    val automaticVoteKeys = property("automaticVoteKeys", AutomaticVoteKeys.DISABLED)
    val voteKeyEmailList = property("voteKeyEmailList", emptyList<String>())
    val verifiedEmailsOnly = property("verifiedEmailsOnly", true)
    val resultsFileHeader = property("resultsFileHeader", "")
    val colorScheme = property("colorScheme", ColorScheme.Blue)
    val voting = property("voting", VotingSettings.Default)

    suspend fun getGeneralSettings() = either {
        GeneralSettings(
            resultsFileHeader = resultsFileHeader.get().bind(),
            colorScheme = colorScheme.get().bind(),
            timeZone = app.time.timeZone.get().bind(),
        )
    }

    suspend fun getVoteKeySettings() = either {
        val votingSettings = voting.get().bind()
        VoteSettings(
            automaticVoteKeys = automaticVoteKeys.get().bind(),
            listOfEmails = voteKeyEmailList.get().bind().joinToString("\n"),
            verifiedEmailsOnly = verifiedEmailsOnly.get().bind(),
            minimumPoints = votingSettings.scale.min,
            maximumPoints = votingSettings.scale.max,
            emptyVoteHandling = votingSettings.emptyVotes,
            scoringMethod = votingSettings.scoring,
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
            voting.set(
                VotingSettings(
                    scale = PointScale(settings.minimumPoints, settings.maximumPoints),
                    emptyVotes = settings.emptyVoteHandling,
                    scoring = settings.scoringMethod,
                )
            )
        ).bindAll()
    }
}

data class GeneralSettings(
    @Field(label = "results.txt header", presentation = FieldPresentation.monospace)
    val resultsFileHeader: String,
    @Label("Color scheme")
    val colorScheme: ColorScheme,
    @Label("Time zone")
    val timeZone: TimeZone,
) : Validateable<GeneralSettings>

data class VoteSettings(
    @Label("Automatic vote keys")
    val automaticVoteKeys: AutomaticVoteKeys,
    @Field(label = "Email list", presentation = FieldPresentation.large)
    val listOfEmails: String,
    @Label("Accept only verified email addresses")
    val verifiedEmailsOnly: Boolean,
    @Label("Minimum points per vote")
    val minimumPoints: Int,
    @Label("Maximum points per vote")
    val maximumPoints: Int,
    @Label("How the empty votes are handled?")
    val emptyVoteHandling: EmptyVoteHandling,
    @Label("How the points are tallied?")
    val scoringMethod: ScoringMethod,
) : Validateable<VoteSettings> {
    fun voteCounting() = VotingSettings(
        scale = PointScale(minimumPoints, maximumPoints),
        emptyVotes = emptyVoteHandling,
        scoring = scoringMethod,
    )
}

enum class AutomaticVoteKeys(val label: String) {
    DISABLED("Voting rights can be acquired only by a vote key"),
    PER_USER("Every new user gets voting rights automatically"),
    PER_IP_ADDRESS("Every new user from distinct IP address gets voting rights automatically"),
    PER_EMAIL("Voting rights are granted according to an email list"),
}