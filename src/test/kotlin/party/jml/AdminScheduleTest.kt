package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.AppServices
import party.jml.partyboi.schedule.NewEvent
import java.util.UUID
import kotlin.test.Test
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

    private suspend fun TestHtmlClient.putJson(url: String, body: String) {
        client.put(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.apply { assertEquals(HttpStatusCode.OK, status) }
    }
}
