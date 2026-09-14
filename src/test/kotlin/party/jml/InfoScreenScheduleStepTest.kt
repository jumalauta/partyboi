package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import party.jml.partyboi.AppServices
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.infoscreen.slides.ScheduleSlide
import party.jml.partyboi.schedule.NewEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

// The single schedule slide steps through the remaining party days, one screen per
// day, before the rotation moves on (here it wraps back, being the only slide).
class InfoScreenScheduleStepTest : PartyboiTester {
    private val tz = TimeZone.of("Europe/Helsinki")

    @Test
    fun testScheduleSlideStepsThroughRemainingDays() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                time.timeZone.set(tz).bind()
                // Anchor on the current party day so both days are "relevant" no
                // matter what time of day the test runs at.
                val day1 = ScheduleSlide.currentPartyDay(Clock.System.now(), tz)
                val day2 = day1.plus(1, DateTimeUnit.DAY)
                listOf(day1, day2).forEachIndexed { index, day ->
                    events.add(
                        NewEvent(
                            name = "Event ${index + 1}",
                            startTime = LocalDateTime(day, LocalTime(12, 0)).toInstant(tz),
                            endTime = null,
                            visible = true,
                        )
                    ).bind()
                }
                screen.syncScheduleSlides().bind()
            }
        }
        // The test app boots (and setupServices runs) on the first request
        it.client.get("/screen")
        val services = app!!
        val day1 = ScheduleSlide.currentPartyDay(Clock.System.now(), tz)
        val day2 = day1.plus(1, DateTimeUnit.DAY)

        // One schedule row exists and the stored slide is dateless
        val rows = services.screen.getSlideSet(SlideSetRow.DEFAULT).getOrNull()!!
        assertEquals(listOf(ScheduleSlide()), rows.map { it.getSlide() })

        fun shownDate() = (services.screen.currentSlide() as ScheduleSlide).date

        // Starting the set resolves the first relevant day
        services.screen.startSlideSet(SlideSetRow.DEFAULT).getOrNull()!!
        assertEquals(day1, shownDate())
        val (stateOnDay1, _) = services.screen.currentState()

        // The next step stays on the same row and advances to the next day
        services.screen.showNextSlideFromSet(SlideSetRow.DEFAULT).getOrNull()!!
        assertEquals(day2, shownDate())
        val (stateOnDay2, _) = services.screen.currentState()
        assertEquals(stateOnDay1.id, stateOnDay2.id)

        // After the last day the rotation resumes; being the only slide, it wraps
        // back to the first relevant day
        services.screen.showNextSlideFromSet(SlideSetRow.DEFAULT).getOrNull()!!
        assertEquals(day1, shownDate())

        services.screen.stopSlideSet()
    }
}
