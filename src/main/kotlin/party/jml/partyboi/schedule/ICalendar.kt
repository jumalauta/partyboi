package party.jml.partyboi.schedule

import kotlinx.datetime.*
import kotlinx.datetime.format.char

object ICalendar {
    fun eventsToIcs(hostname: String, instanceName: String, events: List<Event>): String {
        val now = Clock.System.now()
        val dsl = ICalendarDsl()
        dsl.calendar {
            prop("VERSION", "2.0")
            prop("CALSCALE", "GREGORIAN")
            prop("PRODID", "-//Jumalauta//Partyboi//EN")
            prop("X-WR-CALNAME", instanceName)

            events.forEach {
                event {
                    prop("UID", "${it.id}@${hostname}")
                    prop("DTSTAMP", now)
                    prop("DTSTART", it.startTime)
                    it.endTime?.let { prop("DTEND", it) }
                    prop("SUMMARY", it.name)
                }
            }
        }
        return dsl.toString()
    }

    class ICalendarDsl {
        val ics = StringBuilder()

        override fun toString(): String = ics.toString()

        fun obj(name: String, block: ICalendarDsl.() -> Unit) {
            ics.append("BEGIN:$name\n")
            block()
            ics.append("END:$name\n")
        }

        fun calendar(block: ICalendarDsl.() -> Unit) = obj("VCALENDAR", block)
        fun event(block: ICalendarDsl.() -> Unit) = obj("VEVENT", block)

        fun prop(key: String, value: String) = ics.append("$key:$value\n")
        fun prop(key: String, time: Instant) = ics.append("$key:${formatTime(time)}\n")

        private fun formatTime(time: Instant): String =
            time
                .toLocalDateTime(TimeZone.UTC)
                .format(LocalDateTime.Format {
                    year()
                    monthNumber()
                    dayOfMonth()
                    char('T')
                    hour()
                    minute()
                    second()
                    char('Z')
                })
    }
}