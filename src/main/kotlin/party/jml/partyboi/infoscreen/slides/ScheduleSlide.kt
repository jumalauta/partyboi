@file:UseSerializers(
    LocalDateIso8601Serializer::class,
)

package party.jml.partyboi.infoscreen.slides

import arrow.core.raise.either
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.serializers.LocalDateIso8601Serializer
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.Form
import party.jml.partyboi.infoscreen.NonEditable
import party.jml.partyboi.infoscreen.SlideType
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.displayTime
import party.jml.partyboi.system.toDate
import party.jml.partyboi.validation.Validateable
import java.time.format.TextStyle
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// The stored row is dateless (date = null): the slide decides at display time which
// party days remain relevant, and InfoScreenService emits one dated copy per day when
// its turn comes in the rotation. A concrete date only exists in those emitted copies.
@Serializable
data class ScheduleSlide(
    val date: LocalDate? = null,
) : Slide<ScheduleSlide>, Validateable<ScheduleSlide>, NonEditable {
    override suspend fun render(ctx: FlowContent, app: AppServices) {
        val tz = app.time.timeZone.get().getOrNull()!!
        val day = date ?: firstRelevantDay(app, tz) ?: return
        val from = LocalDateTime(day, SplitDateAt).toInstant(tz)
        val eventsE = app.events.getBetween(from, from.plus(1.days))
        with(ctx) {
            h1 { +"${day.dayOfWeek.toJavaDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK)}" }
            eventsE.map { events ->
                table {
                    events.forEach { event ->
                        tr {
                            th { +event.startTime.displayTime(tz) }
                            td { +event.name }
                        }
                    }
                }
            }
        }
    }

    override fun getForm(): Form<ScheduleSlide> = Form(ScheduleSlide::class, this, true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = date?.let { "Schedule: $it" } ?: "Schedule"
    override fun getType(): SlideType = SlideType("calendar", "Schedule")

    companion object {
        val SplitDateAt: LocalTime = LocalTime(6, 0)

        // The party day `now` belongs to: before 06:00 the previous calendar date is
        // still "today" (a party day runs from SplitDateAt to SplitDateAt).
        fun currentPartyDay(now: Instant, tz: TimeZone): LocalDate {
            val local = now.toLocalDateTime(tz)
            return if (local.time < SplitDateAt) local.date.minus(1, DateTimeUnit.DAY) else local.date
        }

        // Days worth showing, in order: event dates >= the current party day. Before
        // the party that is every day; after it, degrade to the last day so the slide
        // stays meaningful. No event dates at all -> empty.
        fun relevantDays(eventDates: List<LocalDate>, now: Instant, tz: TimeZone): List<LocalDate> {
            val sorted = eventDates.distinct().sorted()
            val today = currentPartyDay(now, tz)
            return sorted.filter { it >= today }.ifEmpty { sorted.takeLast(1) }
        }

        suspend fun eventDates(app: AppServices): AppResult<List<LocalDate>> = either {
            app.events.getPublic().bind().map { it.startTime.toDate() }
        }

        suspend fun firstRelevantDay(app: AppServices, tz: TimeZone): LocalDate? =
            eventDates(app).getOrNull()?.let { relevantDays(it, Clock.System.now(), tz).firstOrNull() }
    }
}
