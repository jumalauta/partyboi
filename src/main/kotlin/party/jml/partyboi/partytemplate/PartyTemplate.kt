package party.jml.partyboi.partytemplate

import arrow.core.left
import arrow.core.right
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.entries.FileFormat
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.LOCAL_TIME_FORMAT
import party.jml.partyboi.triggers.Action
import party.jml.partyboi.triggers.CloseVotingForAllCompos
import party.jml.partyboi.triggers.OpenCloseSubmitting
import party.jml.partyboi.triggers.OpenCloseVoting
import java.util.*
import kotlin.time.Instant

const val PARTY_TEMPLATE_VERSION = 1

val TemplateJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

// A reusable party setup: general compo rules, compos, and the schedule with its
// triggers. Times are wall-clock relative to the party start date so the template
// lands on the importing instance's own party dates. Compos are referenced by their
// index in this file's compos list (names are not unique).
@Serializable
data class PartyTemplate(
    val partyboiTemplate: Int,
    val exportedAt: String = "",
    val instanceName: String = "",
    val generalRules: String? = null,
    // Settings carried over to the next year's instance. All optional so
    // version 1 files written before these existed still parse.
    val partyDays: Int? = null,
    val timeZone: String? = null,
    val resultsFileHeader: String? = null,
    val compos: List<TemplateCompo> = emptyList(),
    val events: List<TemplateEvent> = emptyList(),
)

@Serializable
data class TemplateCompo(
    val name: String,
    val rules: String = "",
    val requireFile: Boolean? = null,
    val fileFormats: List<String> = emptyList(),
    val manualResults: Boolean = false,
    val hideAuthor: Boolean = false,
    val changeoverSec: Int = 20,
    val defaultSlotSec: Int = 60,
) {
    // Unknown format names (from a newer Partyboi) are dropped rather than failing the import.
    fun toCompo() = Compo.Empty.copy(
        id = UUIDv7.Empty,
        name = name,
        rules = rules,
        requireFile = requireFile,
        fileFormats = fileFormats.mapNotNull { name -> FileFormat.entries.find { it.name == name } },
        manualResults = manualResults,
        hideAuthor = hideAuthor,
        changeoverSec = changeoverSec,
        defaultSlotSec = defaultSlotSec,
    )

    companion object {
        fun fromCompo(compo: Compo) = TemplateCompo(
            name = compo.name,
            rules = compo.rules,
            requireFile = compo.requireFile,
            fileFormats = compo.fileFormats.map { it.name },
            manualResults = compo.manualResults,
            hideAuthor = compo.hideAuthor,
            changeoverSec = compo.changeoverSec,
            defaultSlotSec = compo.defaultSlotSec,
        )
    }
}

@Serializable
data class TemplateEvent(
    val name: String,
    val start: RelativeTime,
    val end: RelativeTime? = null,
    val visible: Boolean = true,
    val triggers: List<TemplateTrigger> = emptyList(),
)

// A wall-clock moment relative to the party start date: dayOffset 0 is the start
// date itself (negative for pre-party events) and time is "HH:mm" in the party's
// timezone on that day. Wall-clock beats a duration offset here: "Saturday 14:00"
// stays 14:00 across DST changes and different party lengths.
@Serializable
data class RelativeTime(
    val dayOffset: Int,
    val time: String,
) {
    fun toInstant(partyStart: LocalDate, tzAt: (LocalDate) -> TimeZone): Instant {
        val date = partyStart.plus(dayOffset, DateTimeUnit.DAY)
        return LocalDateTime(date, LocalTime.parse(time)).toInstant(tzAt(date))
    }

    companion object {
        fun fromInstant(instant: Instant, partyStart: LocalDate, tzAt: (LocalDate) -> TimeZone): RelativeTime {
            // The timezone may vary by date (overrides), so resolve the date in two
            // passes: first approximately, then with that date's own timezone.
            val approxDate = instant.toLocalDateTime(tzAt(partyStart)).date
            val local = instant.toLocalDateTime(tzAt(approxDate))
            return RelativeTime(
                dayOffset = partyStart.daysUntil(local.date),
                time = local.time.format(LOCAL_TIME_FORMAT),
            )
        }
    }
}

@Serializable
sealed interface TemplateTrigger {
    // Resolves to a live Action, or null when the target compo is not available in
    // the importing instance (the trigger is then dropped).
    fun toAction(compoIdByIndex: Map<Int, UUID>): Action?

    fun label(compos: List<TemplateCompo>): String
}

private fun compoName(compos: List<TemplateCompo>, index: Int) =
    compos.getOrNull(index)?.name ?: "unknown compo #$index"

@Serializable
@SerialName("openCloseVoting")
data class TemplateOpenCloseVoting(val compoIndex: Int, val open: Boolean) : TemplateTrigger {
    override fun toAction(compoIdByIndex: Map<Int, UUID>) =
        compoIdByIndex[compoIndex]?.let { OpenCloseVoting(it, open) }

    override fun label(compos: List<TemplateCompo>) =
        "${if (open) "Open" else "Close"} voting: ${compoName(compos, compoIndex)}"
}

@Serializable
@SerialName("openCloseSubmitting")
data class TemplateOpenCloseSubmitting(val compoIndex: Int, val open: Boolean) : TemplateTrigger {
    override fun toAction(compoIdByIndex: Map<Int, UUID>) =
        compoIdByIndex[compoIndex]?.let { OpenCloseSubmitting(it, open) }

    override fun label(compos: List<TemplateCompo>) =
        "${if (open) "Open" else "Close"} submitting: ${compoName(compos, compoIndex)}"
}

@Serializable
@SerialName("closeVotingForAllCompos")
data object TemplateCloseVotingForAllCompos : TemplateTrigger {
    override fun toAction(compoIdByIndex: Map<Int, UUID>) = CloseVotingForAllCompos
    override fun label(compos: List<TemplateCompo>) = "Close voting for all compos"
}

fun Action.toTemplateTrigger(compoIndexById: Map<UUID, Int>): TemplateTrigger? = when (this) {
    is OpenCloseVoting -> compoIndexById[compoId]?.let { TemplateOpenCloseVoting(it, open) }
    is OpenCloseSubmitting -> compoIndexById[compoId]?.let { TemplateOpenCloseSubmitting(it, open) }
    is CloseVotingForAllCompos -> TemplateCloseVotingForAllCompos
}

fun parsePartyTemplate(json: String): AppResult<PartyTemplate> =
    try {
        val template = TemplateJson.decodeFromString<PartyTemplate>(json)
        if (template.partyboiTemplate != PARTY_TEMPLATE_VERSION) {
            ValidationError(
                "file",
                "Unsupported template version ${template.partyboiTemplate} (this Partyboi supports version $PARTY_TEMPLATE_VERSION)",
                template.partyboiTemplate.toString()
            ).left()
        } else {
            template.right()
        }
    } catch (e: Exception) {
        ValidationError("file", "Not a valid party template file: ${e.message}", "").left()
    }
