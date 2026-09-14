package party.jml

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.infoscreen.slides.ScheduleSlide
import kotlin.test.Test
import kotlin.test.assertEquals

// Pure tests for the schedule slide's day resolution: which party day "now" belongs
// to (the 06:00 split) and which days the slide still has to show.
class ScheduleSlideDaysTest {
    private val tz = TimeZone.of("Europe/Helsinki")

    private val day1 = LocalDate(2026, 7, 15)
    private val day2 = LocalDate(2026, 7, 16)
    private val day3 = LocalDate(2026, 7, 17)
    private val days = listOf(day1, day2, day3)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(tz)

    @Test
    fun beforeThePartyAllDaysAreRelevant() {
        val now = at(LocalDate(2026, 7, 10), 12)
        assertEquals(days, ScheduleSlide.relevantDays(days, now, tz))
    }

    @Test
    fun midPartyPastDaysAreSkipped() {
        val now = at(day2, 12)
        assertEquals(listOf(day2, day3), ScheduleSlide.relevantDays(days, now, tz))
    }

    @Test
    fun beforeTheSixOClockSplitThePreviousDateIsStillCurrent() {
        // At 02:00 on day 3 the party is still living day 2
        val now = at(day3, 2)
        assertEquals(day2, ScheduleSlide.currentPartyDay(now, tz))
        assertEquals(listOf(day2, day3), ScheduleSlide.relevantDays(days, now, tz))
    }

    @Test
    fun atTheSixOClockSplitTheDayFlips() {
        val now = at(day3, 6)
        assertEquals(day3, ScheduleSlide.currentPartyDay(now, tz))
        assertEquals(listOf(day3), ScheduleSlide.relevantDays(days, now, tz))
    }

    @Test
    fun afterThePartyTheLastDayRemains() {
        val now = at(LocalDate(2026, 7, 20), 12)
        assertEquals(listOf(day3), ScheduleSlide.relevantDays(days, now, tz))
    }

    @Test
    fun noEventDatesMeansNoDays() {
        val now = at(day1, 12)
        assertEquals(emptyList(), ScheduleSlide.relevantDays(emptyList(), now, tz))
    }

    @Test
    fun duplicateAndUnsortedDatesAreNormalized() {
        val now = at(day1, 12)
        assertEquals(days, ScheduleSlide.relevantDays(listOf(day3, day1, day2, day1, day3), now, tz))
    }
}
