package party.jml

import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.ICalendar
import java.util.UUID
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ICalendarTest {

    private fun event(name: String) = Event(
        id = UUID.fromString("0190a0e0-0000-7000-8000-000000000001"),
        name = name,
        startTime = Instant.parse("2025-06-01T12:00:00Z"),
        endTime = null,
        visible = true,
    )

    private fun ics(name: String) = ICalendar.eventsToIcs(
        hostname = "party.example",
        instanceName = "Test Party",
        events = listOf(event(name)),
    )

    // A comma in an event name (e.g. "Demo, Wild & Combined") used to be emitted verbatim, which
    // corrupts the SUMMARY value for strict parsers. Commas and semicolons must be escaped.
    @Test
    fun testCommaAndSemicolonAreEscaped() {
        val out = ics("Demo, Wild; Combined")
        assertTrue(
            out.contains("SUMMARY:Demo\\, Wild\\; Combined"),
            "comma and semicolon must be escaped in SUMMARY:\n$out"
        )
    }

    @Test
    fun testBackslashIsEscaped() {
        val out = ics("Back\\slash")
        assertTrue(out.contains("SUMMARY:Back\\\\slash"), "backslash must be escaped:\n$out")
    }

    // Regression for the iCalendar injection vector: a newline in the event name must be escaped to
    // a literal "\n" sequence, never emitted as a real line break that injects calendar components.
    @Test
    fun testNewlineCannotInjectCalendarComponents() {
        val out = ics("Party\r\nBEGIN:VEVENT\r\nSUMMARY:Injected")
        val structuralVevents = out.split(Regex("\\r\\n|\\n|\\r")).count { it == "BEGIN:VEVENT" }
        assertEquals(1, structuralVevents, "event name newline injected an extra calendar component:\n$out")
        assertTrue(
            out.contains("""SUMMARY:Party\nBEGIN:VEVENT\nSUMMARY:Injected"""),
            "newline must be escaped to a literal \\n:\n$out"
        )
    }

    // RFC 5545 §3.1: no content line may exceed 75 octets.
    @Test
    fun testLongLinesAreFolded() {
        val out = ics("A".repeat(200))
        val tooLong = out.split("\r\n").filter { it.toByteArray(Charsets.UTF_8).size > 75 }
        assertTrue(tooLong.isEmpty(), "content lines must be folded to <= 75 octets; offenders: $tooLong")
        assertTrue(out.contains("\r\n A"), "a folded continuation line must start with a space:\n$out")
    }

    @Test
    fun testMultiByteCharactersAreNotSplitAcrossFold() {
        // 80 'ä' characters (2 octets each in UTF-8): folding must break on character boundaries so
        // each physical line still decodes as valid UTF-8.
        val out = ics("ä".repeat(80))
        out.split("\r\n").forEach { line ->
            assertEquals(
                line,
                String(line.toByteArray(Charsets.UTF_8), Charsets.UTF_8),
                "a fold split a multi-byte UTF-8 sequence"
            )
            assertTrue(line.toByteArray(Charsets.UTF_8).size <= 75, "line exceeds 75 octets: $line")
        }
    }

    @Test
    fun testSkeletonIsWellFormed() {
        val out = ics("Opening ceremony")
        assertTrue(out.startsWith("BEGIN:VCALENDAR\r\n"), out)
        assertTrue(out.trimEnd().endsWith("END:VCALENDAR"), out)
        assertTrue(out.contains("BEGIN:VEVENT\r\n"), out)
        assertTrue(out.contains("SUMMARY:Opening ceremony\r\n"), out)
    }
}
