package party.jml.partyboi.settings

import arrow.core.raise.either
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Label
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.toDate
import party.jml.partyboi.templates.ColorScheme
import party.jml.partyboi.templates.Theme
import party.jml.partyboi.validation.Min
import party.jml.partyboi.validation.Validateable

class SettingsService(app: AppServices) : Service(app) {
    val automaticVoteKeys = property("automaticVoteKeys", AutomaticVoteKeys.DISABLED)
    val voteKeyEmailList = property("voteKeyEmailList", emptyList<String>())
    val verifiedEmailsOnly = property("verifiedEmailsOnly", true)
    val resultsFileHeader = property("resultsFileHeader", "")
    val colorScheme = property("colorScheme", ColorScheme.Blue)
    val wizardCompleted = property("wizardCompleted", false)
    val partyStartDate = property<LocalDate?>("partyStartDate", null)
    val partyDays = property("partyDays", 3)

    suspend fun getGeneralSettings() = either {
        GeneralSettings(
            resultsFileHeader = resultsFileHeader.get().bind(),
            colorScheme = colorScheme.get().bind(),
            timeZone = app.time.timeZone.get().bind(),
            partyStartDate = partyStartDate.get().bind(),
            partyDays = partyDays.get().bind(),
        )
    }

    // The days the party runs on, from the configured start date and length.
    // Empty when the start date has not been set.
    suspend fun partyDates(): AppResult<List<LocalDate>> = either {
        val start = partyStartDate.get().bind() ?: return@either emptyList()
        val days = partyDays.get().bind().coerceAtLeast(1)
        (0 until days).map { start.plus(it, DateTimeUnit.DAY) }
    }

    // Installations that completed the wizard before these settings existed have no
    // party dates; derive them once from the schedule so the day dropdowns work
    // without re-running the wizard. A database with no events is left untouched.
    suspend fun initPartyDatesFromSchedule(): AppResult<Unit> = either {
        if (partyStartDate.get().bind() != null) return@either
        val events = app.events.getAll().bind()
        if (events.isEmpty()) return@either
        val dates = events.flatMap { listOfNotNull(it.startTime.toDate(), it.endTime?.toDate()) }
        val first = dates.min()
        partyStartDate.set(first).bind()
        partyDays.set((first.daysUntil(dates.max()) + 1).coerceAtLeast(1)).bind()
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
            partyStartDate.set(settings.partyStartDate),
            partyDays.set(settings.partyDays),
        ).bindAll()
    }

    suspend fun saveSettings(settings: VoteSettings) = either {
        if (settings.automaticVoteKeys == AutomaticVoteKeys.PER_EMAIL && !app.email.isConfigured()) {
            raise(
                ValidationError(
                    "automaticVoteKeys",
                    "Requires a configured email service",
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
    @Field(label = "results.txt header", presentation = FieldPresentation.monospace)
    val resultsFileHeader: String,
    @Label("Color scheme")
    val colorScheme: ColorScheme,
    @Label("Time zone")
    val timeZone: TimeZone,
    @Label("Party start date")
    val partyStartDate: LocalDate?,
    @Label("Party length (days)")
    @Min(1)
    val partyDays: Int,
) : Validateable<GeneralSettings>

data class VoteSettings(
    @Label("Automatic vote keys")
    val automaticVoteKeys: AutomaticVoteKeys,
    @Field(label = "Email list", presentation = FieldPresentation.large)
    val listOfEmails: String,
    @Label("Accept only verified email addresses")
    val verifiedEmailsOnly: Boolean,
) : Validateable<VoteSettings>

enum class AutomaticVoteKeys(val label: String) {
    DISABLED("Vote keys only"),
    PER_USER("Every new user gets voting rights automatically"),
    PER_IP_ADDRESS("Every new user from distinct IP address gets voting rights automatically"),
    PER_EMAIL("Voting rights are granted according to an email list"),
}