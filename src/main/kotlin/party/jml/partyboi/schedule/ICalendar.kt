package party.jml.partyboi.schedule

import kotlinx.datetime.*
import kotlinx.datetime.format.char
import kotlin.time.Clock
import kotlin.time.Instant

object ICalendar {
    fun eventsToIcs(hostname: String, instanceName: String, events: List<Event>): String {
        val now = Clock.System.now()
        val dsl = ICalendarDsl()
        dsl.calendar {
            prop("VERSION", "2.0")
            prop("CALSCALE", "GREGORIAN")
            // METHOD:PUBLISH marks this as a published, read-only feed. Strict consumers (notably
            // Outlook) treat a feed without it differently; Google is lenient but expects it too.
            prop("METHOD", "PUBLISH")
            prop("PRODID", "-//Jumalauta//Partyboi//EN")
            prop("X-WR-CALNAME", instanceName)
            // Refresh hints so subscribers re-poll promptly instead of relying on their (often
            // multi-hour) defaults. REFRESH-INTERVAL is the RFC 7986 property; X-PUBLISHED-TTL is
            // the older Microsoft/Google extension that the same clients still honour.
            prop("REFRESH-INTERVAL;VALUE=DURATION", "PT1H")
            prop("X-PUBLISHED-TTL", "PT1H")

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
        val CRLF = "\r\n"

        override fun toString(): String = ics.toString()

        fun append(line: String) {
            ics.append(fold(line)).append(CRLF)
        }

        fun obj(name: String, block: ICalendarDsl.() -> Unit) {
            append("BEGIN:$name")
            block()
            append("END:$name")
        }

        fun calendar(block: ICalendarDsl.() -> Unit) = obj("VCALENDAR", block)
        fun event(block: ICalendarDsl.() -> Unit) = obj("VEVENT", block)

        fun prop(key: String, value: String) = append("$key:${escapeText(value)}")
        fun prop(key: String, time: Instant) = append("$key:${formatTime(time)}")

        // RFC 5545 §3.3.11: backslash, semicolon, comma and newlines are special in TEXT values and
        // must be escaped. Escape the backslash first so the escapes added afterwards aren't doubled.
        private fun escapeText(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n")
                .replace(";", "\\;")
                .replace(",", "\\,")

        // RFC 5545 §3.1: a content line is limited to 75 octets; longer lines must be folded by
        // inserting CRLF followed by a single space. Fold on character boundaries so multi-byte
        // UTF-8 sequences are never split across a fold.
        private fun fold(line: String): String {
            val maxOctets = 75
            val sb = StringBuilder()
            var octets = 0
            var i = 0
            while (i < line.length) {
                val codePoint = line.codePointAt(i)
                val charCount = Character.charCount(codePoint)
                val piece = line.substring(i, i + charCount)
                val pieceOctets = piece.toByteArray(Charsets.UTF_8).size
                if (octets + pieceOctets > maxOctets) {
                    sb.append(CRLF).append(' ')
                    octets = 1 // the continuation line begins with the inserted space
                }
                sb.append(piece)
                octets += pieceOctets
                i += charCount
            }
            return sb.toString()
        }

        private fun formatTime(time: Instant): String =
            time
                .toLocalDateTime(TimeZone.UTC)
                .format(LocalDateTime.Format {
                    year()
                    monthNumber()
                    day()
                    char('T')
                    hour()
                    minute()
                    second()
                    char('Z')
                })
    }
}