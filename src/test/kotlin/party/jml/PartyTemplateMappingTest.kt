package party.jml

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.partytemplate.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyTemplateMappingTest {
    private val tz = TimeZone.of("Europe/Helsinki")
    private val tzAt: (LocalDate) -> TimeZone = { tz }

    @Test
    fun testRelativizeAndAbsolutizeRoundtrip() {
        val partyStart = LocalDate(2025, 8, 1)
        val instant = LocalDateTime(2025, 8, 2, 14, 30).toInstant(tz)

        val relative = RelativeTime.fromInstant(instant, partyStart, tzAt)
        assertEquals(1, relative.dayOffset)
        assertEquals("14:30", relative.time)
        assertEquals(instant, relative.toInstant(partyStart, tzAt))
    }

    @Test
    fun testNegativeDayOffsetForPrePartyEvent() {
        val partyStart = LocalDate(2025, 8, 1)
        val instant = LocalDateTime(2025, 7, 30, 18, 0).toInstant(tz)

        val relative = RelativeTime.fromInstant(instant, partyStart, tzAt)
        assertEquals(-2, relative.dayOffset)
        assertEquals("18:00", relative.time)
        assertEquals(instant, relative.toInstant(partyStart, tzAt))
    }

    @Test
    fun testAbsolutizeAgainstDifferentPartyStartKeepsWallClock() {
        val sourceStart = LocalDate(2025, 8, 1)
        val targetStart = LocalDate(2026, 7, 15)
        val instant = LocalDateTime(2025, 8, 3, 12, 0).toInstant(tz)

        val relative = RelativeTime.fromInstant(instant, sourceStart, tzAt)
        val mapped = relative.toInstant(targetStart, tzAt)

        assertEquals(LocalDateTime(2026, 7, 17, 12, 0).toInstant(tz), mapped)
    }

    @Test
    fun testEndTimeAfterMidnightLandsOnNextDay() {
        val partyStart = LocalDate(2025, 8, 1)
        val end = RelativeTime(dayOffset = 1, time = "01:00")

        val mapped = end.toInstant(partyStart, tzAt)
        assertEquals(LocalDateTime(2025, 8, 2, 1, 0).toInstant(tz), mapped)
    }

    @Test
    fun testTemplateJsonRoundtrip() {
        val template = PartyTemplate(
            partyboiTemplate = PARTY_TEMPLATE_VERSION,
            exportedAt = "2026-09-13T12:00:00Z",
            instanceName = "Test Party",
            generalRules = "Be nice",
            compos = listOf(
                TemplateCompo(
                    name = "4k intro",
                    rules = "Max 4096 bytes",
                    requireFile = true,
                    fileFormats = listOf("Zip"),
                    manualResults = false,
                    hideAuthor = true,
                    changeoverSec = 30,
                    defaultSlotSec = 90,
                )
            ),
            events = listOf(
                TemplateEvent(
                    name = "4k deadline",
                    start = RelativeTime(1, "12:00"),
                    end = RelativeTime(1, "13:00"),
                    visible = true,
                    triggers = listOf(
                        TemplateOpenCloseSubmitting(compoIndex = 0, open = false),
                        TemplateCloseVotingForAllCompos,
                    ),
                )
            ),
        )

        val json = TemplateJson.encodeToString(template)
        val decoded = TemplateJson.decodeFromString<PartyTemplate>(json)
        assertEquals(template, decoded)
    }

    @Test
    fun testParseToleratesUnknownKeys() {
        val json = """{"partyboiTemplate": 1, "somethingFromTheFuture": true, "compos": [{"name": "demo"}]}"""
        val result = parsePartyTemplate(json)
        assertTrue(result.isRight(), "Unknown keys should not fail parsing: $result")
        assertEquals("demo", result.getOrNull()?.compos?.first()?.name)
    }

    @Test
    fun testParseRejectsUnsupportedVersion() {
        val result = parsePartyTemplate("""{"partyboiTemplate": 999}""")
        assertTrue(result.isLeft(), "Future versions must be rejected: $result")
    }

    @Test
    fun testParseRejectsGarbage() {
        assertTrue(parsePartyTemplate("not json").isLeft())
        assertTrue(parsePartyTemplate("""{"foo": "bar"}""").isLeft())
    }

    // The example template shipped in the repository root must stay importable.
    @Test
    fun testExampleTemplateInRepositoryRootIsValid() {
        val json = java.io.File("example-party-template.json").readText()
        val result = parsePartyTemplate(json)
        assertTrue(result.isRight(), "example-party-template.json must parse: $result")

        val template = result.getOrNull()!!
        assertTrue(template.compos.isNotEmpty())
        assertTrue(template.events.isNotEmpty())
        template.events.flatMap { it.triggers }.forEach { trigger ->
            when (trigger) {
                is TemplateOpenCloseVoting -> assertTrue(trigger.compoIndex in template.compos.indices)
                is TemplateOpenCloseSubmitting -> assertTrue(trigger.compoIndex in template.compos.indices)
                is TemplateCloseVotingForAllCompos -> {}
            }
        }
    }
}
