package party.jml.partyboi.schedule

import kotlinx.datetime.toJavaLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ICalendar {
    fun eventsToIcs(events: List<Event>): String {
        val now = LocalDateTime.now()
        val dsl = ICalendarDsl()
        dsl.calendar {
            prop("VERSION", "2.0")
            prop("CALSCALE", "GREGORIAN")
            prop("PRODID", "-//Jumalauta//Partyboi//EN")

            events.forEach {
                event {
                    prop("UID", "${it.id}@partyboi.app")
                    prop("DTSTAMP", now)
                    prop("DTSTART", it.time)
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
        fun prop(key: String, time: LocalDateTime) = ics.append("$key:${formatTime(time)}\n")
        fun prop(key: String, time: kotlinx.datetime.LocalDateTime) = prop(key, time.toJavaLocalDateTime())

        private fun formatTime(time: LocalDateTime): String =
            time.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
    }
}