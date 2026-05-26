package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.AppServices
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
}
