@file:UseSerializers(
    LocalDateIso8601Serializer::class,
)

package party.jml.partyboi.screen.slides

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.serializers.LocalDateIso8601Serializer
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.NonEditable
import party.jml.partyboi.screen.Slide
import party.jml.partyboi.screen.SlideType
import java.time.format.TextStyle
import java.util.*
import kotlin.time.Duration.Companion.days

@Serializable
data class ScheduleSlide(
    val date: LocalDate,
) : Slide<ScheduleSlide>, Validateable<ScheduleSlide>, NonEditable {
    override fun render(ctx: FlowContent, app: AppServices) {
        val from = LocalDateTime(date, SplitDateAt)
        val eventsE = app.events.getBetween(from, app.time.addSync(from, 1.days))
        with(ctx) {
            h1 { +"${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)}" }
            eventsE.map { events ->
                table {
                    events.forEach { event ->
                        tr {
                            th { +event.time.time.toString() }
                            td { +event.name }
                        }
                    }
                }
            }
        }
    }

    override fun getForm(): Form<ScheduleSlide> = Form(ScheduleSlide::class, this, true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = "Schedule: $date"
    override fun getType(): SlideType = SlideType("calendar", "Schedule")

    companion object {
        val SplitDateAt: LocalTime = LocalTime(6, 0)
    }
}