package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.AppServices
import party.jml.partyboi.schedule.NewEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminSettingsTest : PartyboiTester {
    @Test
    fun testSettingsPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/settings", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testSettingsPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/settings", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Settings") }
        }
    }

    @Test
    fun testPartyDateSettingsRoundTrip() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }
        it.login("admin")

        it.post("/admin/settings", formData {
            append("resultsFileHeader", "")
            append("colorScheme", "Blue")
            append("timeZone", "Europe/Helsinki")
            append("partyStartDate", "2026-07-15")
            append("partyDays", "4")
        }) {
            it.redirectsTo("/admin/settings")
        }

        assertEquals(LocalDate(2026, 7, 15), app!!.settings.partyStartDate.get().getOrNull())
        assertEquals(4, app!!.settings.partyDays.get().getOrNull())
        assertEquals(
            (15..18).map { LocalDate(2026, 7, it) },
            app!!.settings.partyDates().getOrNull(),
        )

        it.get("/admin/settings", HttpStatusCode.OK) {
            relaxed = true
            findFirst("input[name=partyStartDate]") { attribute("value").toBe("2026-07-15") }
            findFirst("input[name=partyDays]") { attribute("value").toBe("4") }
        }
    }

    // Installations from before the party date settings existed derive them from
    // the schedule at application start; an empty schedule leaves them unset.
    @Test
    fun testPartyDatesDerivedFromSchedule() = test {
        var app: AppServices? = null
        val tz = TimeZone.currentSystemDefault()
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }
        // setupServices runs when the test server starts; a request triggers it.
        it.login("admin")

        app!!.settings.initPartyDatesFromSchedule().getOrNull()
        assertNull(app!!.settings.partyStartDate.get().getOrNull())

        app!!.events.add(NewEvent("A", LocalDateTime(2026, 6, 1, 12, 0).toInstant(tz), null, true))
        app!!.events.add(NewEvent("B", LocalDateTime(2026, 6, 3, 12, 0).toInstant(tz), null, true))

        app!!.settings.initPartyDatesFromSchedule().getOrNull()
        assertEquals(LocalDate(2026, 6, 1), app!!.settings.partyStartDate.get().getOrNull())
        assertEquals(3, app!!.settings.partyDays.get().getOrNull())

        // Already-configured dates are not overwritten by a later start.
        app!!.events.add(NewEvent("C", LocalDateTime(2026, 5, 1, 12, 0).toInstant(tz), null, true))
        app!!.settings.initPartyDatesFromSchedule().getOrNull()
        assertEquals(LocalDate(2026, 6, 1), app!!.settings.partyStartDate.get().getOrNull())
    }
}