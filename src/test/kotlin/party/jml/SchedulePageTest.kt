package party.jml

import arrow.core.raise.either
import arrow.core.right
import it.skrape.matchers.toBe
import it.skrape.selects.html5.article
import it.skrape.selects.html5.h1
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.schedule.NewEvent
import kotlin.test.Test

class SchedulePageTest : PartyboiTester {
    @Test
    fun testEmptySchedulePage() = test {
        setupServices { Unit.right() }
        it.get("/schedule") {
            h1 { findFirst { text.toBe("Schedule") } }
        }
    }

    @Test
    fun testSchedulePageWithEvents() = test {
        setupServices {
            either {
                events.add(
                    NewEvent(
                        name = "Opening ceremony",
                        startTime = LocalDateTime(2025, 6, 1, 12, 0).toInstant(TimeZone.currentSystemDefault()),
                        visible = true
                    )
                ).bind()
                events.add(
                    NewEvent(
                        name = "Hidden event",
                        startTime = LocalDateTime(2025, 6, 1, 14, 0).toInstant(TimeZone.currentSystemDefault()),
                        visible = false
                    )
                ).bind()
            }
        }

        it.get("/schedule") {
            relaxed = true
            article {
                findFirst { text.containsNone("Hidden event") }
            }
        }
    }
}

private fun String.containsNone(substring: String) {
    if (contains(substring)) {
        throw AssertionError("Expected text to not contain '$substring', but it did: $this")
    }
}