package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.AppServices
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.infoscreen.slides.ScheduleSlide
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AdminScheduleTest : PartyboiTester {
    @Test
    fun testAdminSchedulePageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/schedule", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAdminSchedulePageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/schedule", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Schedule") }
        }
    }

    // Regression: when the admin enters a wall-clock event time, the server must
    // interpret it using the timezone that's effective on the picked date, not
    // the offset that happens to apply today. Posting both a summer and a winter
    // date with Europe/Helsinki configured exercises both DST sides.
    @Test
    fun testEventTimesAcrossDstBoundary() = test {
        var app: AppServices? = null
        val originalTimeZone = TimeZone.currentSystemDefault()

        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                time.timeZone.set(TimeZone.of("Europe/Helsinki")).bind()
            }
        }

        it.login("admin")

        try {
            it.post("/admin/schedule/events", formData {
                append("name", "Summer Event")
                append("startTime", "2026-07-15T22:00:00")
                append("endTime", "")
                append("visible", "true")
            }) { _ -> }

            it.post("/admin/schedule/events", formData {
                append("name", "Winter Event")
                append("startTime", "2026-12-31T22:00:00")
                append("endTime", "")
                append("visible", "true")
            }) { _ -> }

            val events = app!!.events.getAll().getOrNull()!!
            val summer = events.first { it.name == "Summer Event" }
            val winter = events.first { it.name == "Winter Event" }

            // 22:00 Helsinki in July (DST, +03:00) → 19:00 UTC
            assertEquals(
                LocalDateTime(2026, 7, 15, 19, 0).toInstant(TimeZone.UTC),
                summer.startTime,
            )
            // 22:00 Helsinki in December (standard, +02:00) → 20:00 UTC
            assertEquals(
                LocalDateTime(2026, 12, 31, 20, 0).toInstant(TimeZone.UTC),
                winter.startTime,
            )
        } finally {
            app?.time?.timeZone?.set(originalTimeZone)
        }
    }

    // Inline editable cells: each cell PUTs its single value. Names save as-is,
    // start times go through the DST-aware wall-clock parser, visibility toggles.
    @Test
    fun testInlineEditEndpoints() = test {
        var app: AppServices? = null
        val originalTimeZone = TimeZone.currentSystemDefault()
        var summerId: UUID? = null
        var winterId: UUID? = null

        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                time.timeZone.set(TimeZone.of("Europe/Helsinki")).bind()
                summerId = events.add(
                    NewEvent("Summer", LocalDateTime(2026, 7, 15, 12, 0).toInstant(TimeZone.UTC), null, true)
                ).bind().id
                winterId = events.add(
                    NewEvent("Winter", LocalDateTime(2026, 12, 31, 12, 0).toInstant(TimeZone.UTC), null, true)
                ).bind().id
            }
        }

        it.login("admin")

        try {
            it.putJson("/admin/schedule/events/$summerId/name", """{"value":"Renamed"}""")
            // Inline time edits are time-only: the value is a wall-clock time of day that
            // is combined with the event's existing date (DST-correct for that date).
            it.putJson("/admin/schedule/events/$summerId/startTime", """{"value":"22:00"}""")
            it.putJson("/admin/schedule/events/$winterId/startTime", """{"value":"22:00"}""")
            it.buttonClick("/admin/schedule/events/$summerId/setVisible/false")

            val events = app!!.events.getAll().getOrNull()!!.associateBy { it.id }
            assertEquals("Renamed", events[summerId]!!.name)
            assertEquals(false, events[summerId]!!.visible)
            // 22:00 on 2026-07-15 in Helsinki (DST, +03:00) → 19:00 UTC
            assertEquals(
                LocalDateTime(2026, 7, 15, 19, 0).toInstant(TimeZone.UTC),
                events[summerId]!!.startTime,
            )
            // 22:00 on 2026-12-31 in Helsinki (standard, +02:00) → 20:00 UTC
            assertEquals(
                LocalDateTime(2026, 12, 31, 20, 0).toInstant(TimeZone.UTC),
                events[winterId]!!.startTime,
            )
        } finally {
            app?.time?.timeZone?.set(originalTimeZone)
        }
    }

    // ±N nudge moves one event (start and end) preserving duration; shift-following
    // moves the chosen event and every later one, leaving earlier events untouched.
    @Test
    fun testNudgeAndShiftFollowing() = test {
        var app: AppServices? = null
        val tz = TimeZone.currentSystemDefault()
        fun at(h: Int, m: Int) = LocalDateTime(2026, 6, 1, h, m).toInstant(tz)
        var e1: UUID? = null
        var e2: UUID? = null
        var e3: UUID? = null

        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                e1 = events.add(NewEvent("E1", at(12, 0), null, true)).bind().id
                e2 = events.add(NewEvent("E2", at(14, 0), at(15, 0), true)).bind().id
                e3 = events.add(NewEvent("E3", at(16, 0), null, true)).bind().id
            }
        }

        it.login("admin")

        it.buttonClick("/admin/schedule/events/$e2/nudge/15")
        var events = app!!.events.getAll().getOrNull()!!.associateBy { it.id }
        assertEquals(at(14, 15), events[e2]!!.startTime)
        assertEquals(at(15, 15), events[e2]!!.endTime)

        it.buttonClick("/admin/schedule/shift/$e2/30")
        events = app!!.events.getAll().getOrNull()!!.associateBy { it.id }
        assertEquals(at(12, 0), events[e1]!!.startTime)   // earlier event untouched
        assertEquals(at(14, 45), events[e2]!!.startTime)  // 14:15 + 30
        assertEquals(at(16, 30), events[e3]!!.startTime)  // 16:00 + 30
    }

    // The shift cascade stops at the first gap of >= 3 hours between consecutive events.
    @Test
    fun testShiftStopsAtThreeHourGap() = test {
        var app: AppServices? = null
        val tz = TimeZone.currentSystemDefault()
        fun at(h: Int, m: Int) = LocalDateTime(2026, 6, 1, h, m).toInstant(tz)
        var e1: UUID? = null
        var e2: UUID? = null
        var e3: UUID? = null
        var e4: UUID? = null

        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                e1 = events.add(NewEvent("E1", at(12, 0), null, true)).bind().id
                e2 = events.add(NewEvent("E2", at(14, 0), at(15, 0), true)).bind().id
                // gap from E2's end (15:00) to E3 (18:00) is exactly 3h -> cascade stops before E3
                e3 = events.add(NewEvent("E3", at(18, 0), null, true)).bind().id
                e4 = events.add(NewEvent("E4", at(19, 0), null, true)).bind().id
            }
        }

        it.login("admin")
        it.buttonClick("/admin/schedule/shift/$e1/30")

        val events = app!!.events.getAll().getOrNull()!!.associateBy { it.id }
        assertEquals(at(12, 30), events[e1]!!.startTime)  // chosen event shifted
        assertEquals(at(14, 30), events[e2]!!.startTime)  // gap 12->14 is 2h, still cascades
        assertEquals(at(18, 0), events[e3]!!.startTime)   // >= 3h gap: not shifted
        assertEquals(at(19, 0), events[e4]!!.startTime)   // everything after the gap stays put
    }

    // The "new event row" endpoint scaffolds a blank event whose start time follows
    // the last one, so repeated use chains events for keyboard-driven entry.
    @Test
    fun testCreateEmptyEventRow() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }

        it.login("admin")

        it.client.post("/admin/schedule/events/new").apply { assertEquals(HttpStatusCode.OK, status) }
        var events = app!!.events.getAll().getOrNull()!!
        assertEquals(1, events.size)
        assertEquals("", events[0].name)

        it.client.post("/admin/schedule/events/new").apply { assertEquals(HttpStatusCode.OK, status) }
        events = app!!.events.getAll().getOrNull()!!
        assertEquals(2, events.size)
        assertTrue(events[1].startTime >= events[0].startTime)
    }

    // Regression: editing the time of a freshly-scaffolded (still unnamed) row must
    // succeed — the inline time endpoints validate only start/end ordering, not the
    // required name. Ordering is still enforced.
    @Test
    fun testInlineTimeEditOnUnnamedRow() = test {
        var app: AppServices? = null
        val tz = TimeZone.currentSystemDefault()
        fun at(h: Int, m: Int) = LocalDateTime(2026, 6, 1, h, m).toInstant(tz)
        var id: UUID? = null

        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                id = events.add(NewEvent("", at(18, 0), null, true)).bind().id
            }
        }

        it.login("admin")

        // Times save despite the empty name
        it.putJson("/admin/schedule/events/$id/startTime", """{"value":"23:00"}""")
        it.putJson("/admin/schedule/events/$id/endTime", """{"value":"23:30"}""")
        val saved = app!!.events.get(id!!).getOrNull()!!
        assertEquals("", saved.name)
        assertEquals(at(23, 0), saved.startTime)
        assertEquals(at(23, 30), saved.endTime)

        // Ordering is still enforced: an end before the start is rejected and ignored
        it.client.put("/admin/schedule/events/$id/endTime") {
            contentType(ContentType.Application.Json)
            setBody("""{"value":"22:00"}""")
        }.apply { assertNotEquals(HttpStatusCode.OK, status) }
        assertEquals(at(23, 30), app!!.events.get(id!!).getOrNull()!!.endTime)
    }

    // Adding (or moving) an event to a date that has no schedule slide yet generates a
    // visible schedule slide for it in the default slide set. Generation is idempotent:
    // adding more events on a date that already has a slide does not duplicate it.
    @Test
    fun testEventChangesGenerateScheduleSlides() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }

        it.login("admin")

        // A new date produces a visible schedule slide
        it.post("/admin/schedule/events", eventForm("First", 1)) { _ -> }
        assertEquals(setOf(LocalDate(2026, 6, 1)), visibleScheduleSlideDates(app!!))

        // Another event on the same date does not create a duplicate slide
        it.post("/admin/schedule/events", eventForm("Second", 1)) { _ -> }
        assertEquals(setOf(LocalDate(2026, 6, 1)), visibleScheduleSlideDates(app!!))

        // A second date adds a second slide
        it.post("/admin/schedule/events", eventForm("Third", 2)) { _ -> }
        assertEquals(setOf(LocalDate(2026, 6, 1), LocalDate(2026, 6, 2)), visibleScheduleSlideDates(app!!))
    }

    // When a date loses its last public event — by deletion or by being hidden — its
    // now-empty schedule slide is removed. A date that still has other public events
    // keeps its slide.
    @Test
    fun testEmptyScheduleSlidesAreRemoved() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }

        it.login("admin")

        // Day 1 has two events, day 2 has one -> a slide for each date
        it.post("/admin/schedule/events", eventForm("A1", 1)) { _ -> }
        it.post("/admin/schedule/events", eventForm("A2", 1)) { _ -> }
        it.post("/admin/schedule/events", eventForm("B", 2)) { _ -> }
        assertEquals(setOf(LocalDate(2026, 6, 1), LocalDate(2026, 6, 2)), visibleScheduleSlideDates(app!!))

        suspend fun eventId(name: String) = app!!.events.getAll().getOrNull()!!.first { it.name == name }.id

        // Deleting the only event on day 2 removes its slide
        it.client.delete("/admin/schedule/events/${eventId("B")}")
            .apply { assertEquals(HttpStatusCode.OK, status) }
        assertEquals(setOf(LocalDate(2026, 6, 1)), visibleScheduleSlideDates(app!!))

        // Deleting one of two events on day 1 keeps the slide (still a public event there)
        it.client.delete("/admin/schedule/events/${eventId("A1")}")
            .apply { assertEquals(HttpStatusCode.OK, status) }
        assertEquals(setOf(LocalDate(2026, 6, 1)), visibleScheduleSlideDates(app!!))

        // Hiding the last remaining event on day 1 removes its slide too
        it.buttonClick("/admin/schedule/events/${eventId("A2")}/setVisible/false")
        assertEquals(emptySet(), visibleScheduleSlideDates(app!!))
    }

    private fun eventForm(name: String, day: Int) = formData {
        append("name", name)
        append("startTime", "2026-06-0${day}T12:00:00")
        append("endTime", "")
        append("visible", "true")
    }

    private suspend fun visibleScheduleSlideDates(app: AppServices): Set<LocalDate> =
        app.screen.getSlideSet(SlideSetRow.DEFAULT).getOrNull()!!
            .mapNotNull { row -> (row.getSlide() as? ScheduleSlide)?.takeIf { row.visible }?.date }
            .toSet()

    private suspend fun TestHtmlClient.putJson(url: String, body: String) {
        client.put(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.apply { assertEquals(HttpStatusCode.OK, status) }
    }
}
