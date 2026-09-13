package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.FileFormat
import party.jml.partyboi.partytemplate.*
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.triggers.CloseVotingForAllCompos
import party.jml.partyboi.triggers.OpenCloseSubmitting
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyTemplateTest : PartyboiTester {
    private val tz = TimeZone.of("Europe/Helsinki")

    private fun testTemplate() = PartyTemplate(
        partyboiTemplate = PARTY_TEMPLATE_VERSION,
        generalRules = "Be nice",
        partyDays = 4,
        timeZone = "Europe/Helsinki",
        resultsFileHeader = "Test Party results",
        compos = listOf(
            TemplateCompo(
                name = "4k intro",
                rules = "Max 4096 bytes",
                requireFile = true,
                fileFormats = listOf("zip"),
                hideAuthor = true,
                changeoverSec = 30,
                defaultSlotSec = 90,
            )
        ),
        events = listOf(
            TemplateEvent(
                name = "4k deadline",
                start = RelativeTime(1, "12:00"),
                visible = true,
                triggers = listOf(
                    TemplateOpenCloseSubmitting(compoIndex = 0, open = false),
                    TemplateCloseVotingForAllCompos,
                ),
            )
        ),
    )

    private fun templateFilePart(json: String) = formData {
        append("file", json.toByteArray(), headers {
            append("Content-Type", "application/json")
            append("Content-Disposition", "form-data; name=\"file\"; filename=\"template.json\"")
        })
    }

    @Test
    fun testExportTemplate() = test {
        var compoId: UUID? = null
        var eventId: UUID? = null
        setupServices {
            either {
                addTestAdmin(this@setupServices).bind()
                time.timeZone.set(tz).bind()
                settings.partyStartDate.set(LocalDate(2025, 8, 1)).bind()
                settings.partyDays.set(4).bind()
                settings.resultsFileHeader.set("Example results header").bind()
                compos.generalRules.set(GeneralRules("Be excellent")).bind()
                val compo = compos.add(NewCompo("4k intro", "Max 4096 bytes")).bind()
                compos.update(
                    compo.copy(
                        requireFile = true,
                        fileFormats = listOf(FileFormat.zip),
                        hideAuthor = true,
                        changeoverSec = 30,
                        defaultSlotSec = 90,
                    )
                ).bind()
                compoId = compo.id
                val (event, _) = events.add(
                    NewEvent("4k deadline", LocalDateTime(2025, 8, 2, 12, 0).toInstant(tz), null, true),
                    listOf(OpenCloseSubmitting(compo.id, false), CloseVotingForAllCompos),
                ).bind()
                eventId = event.id
            }
        }
        it.login("admin")

        // The export page lists every item as a checked checkbox.
        it.get("/admin/settings/export", HttpStatusCode.OK) {
            relaxed = true
            findFirst("input[name=compoIds]") { attribute("value").toBe(compoId.toString()) }
            findFirst("input[name=eventIds]") { attribute("value").toBe(eventId.toString()) }
            // The response is a download, so the form opts out of the submit progress
            // bar (it would never clear without a page navigation).
            findFirst("form") { attribute("data-no-progress").toBe("true") }
        }

        val response = it.client.submitFormWithBinaryData("/admin/settings/export", formData {
            append("generalRules", "on")
            append("partyDays", "on")
            append("timeZone", "on")
            append("resultsFileHeader", "on")
            append("compoIds", compoId.toString())
            append("eventIds", eventId.toString())
        })
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.headers[HttpHeaders.ContentDisposition]?.contains("attachment") == true,
            "Export should be an attachment download"
        )

        val template = TemplateJson.decodeFromString<PartyTemplate>(response.bodyAsText())
        assertEquals(PARTY_TEMPLATE_VERSION, template.partyboiTemplate)
        assertEquals("Be excellent", template.generalRules)
        assertEquals(4, template.partyDays)
        assertEquals("Europe/Helsinki", template.timeZone)
        assertEquals("Example results header", template.resultsFileHeader)

        assertEquals(1, template.compos.size)
        val compo = template.compos.first()
        assertEquals("4k intro", compo.name)
        assertEquals("Max 4096 bytes", compo.rules)
        assertEquals(true, compo.requireFile)
        assertEquals(listOf("zip"), compo.fileFormats)
        assertEquals(true, compo.hideAuthor)
        assertEquals(30, compo.changeoverSec)
        assertEquals(90, compo.defaultSlotSec)

        assertEquals(1, template.events.size)
        val event = template.events.first()
        assertEquals("4k deadline", event.name)
        assertEquals(RelativeTime(1, "12:00"), event.start)
        assertEquals(null, event.end)
        assertTrue(event.triggers.contains(TemplateOpenCloseSubmitting(0, false)), "triggers: ${event.triggers}")
        assertTrue(event.triggers.contains(TemplateCloseVotingForAllCompos), "triggers: ${event.triggers}")
    }

    @Test
    fun testImportTemplateAndSkipDuplicates() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                // The template's timezone (Helsinki) differs from the instance's;
                // importing it must place events in the imported zone.
                time.timeZone.set(TimeZone.UTC).bind()
                // A different party start date than the exporting instance's.
                settings.partyStartDate.set(LocalDate(2026, 7, 15)).bind()
            }
        }
        it.login("admin")

        val json = TemplateJson.encodeToString(testTemplate())

        // Upload shows the selection page with the template stashed in a hidden field.
        val payload = it.post("/admin/settings/import", templateFilePart(json)) {
            findFirst("input[name=payload]") { attribute("value") }
        }
        assertTrue(payload.isNotBlank(), "Selection page must carry the template payload")

        it.post("/admin/settings/import/confirm", formData {
            append("payload", payload)
            append("generalRules", "on")
            append("partyDays", "on")
            append("timeZone", "on")
            append("resultsFileHeader", "on")
            append("compos", "0")
            append("events", "0")
        }) {
            findFirst("h1") { text.toBe("Party template imported") }
        }

        val services = app!!
        assertEquals("Be nice", services.compos.generalRules.get().getOrNull()!!.rules)
        assertEquals(4, services.settings.partyDays.get().getOrNull())
        assertEquals(tz, services.time.timeZone.get().getOrNull())
        assertEquals("Test Party results", services.settings.resultsFileHeader.get().getOrNull())

        val compos = services.compos.getAllCompos().getOrNull()!!
        assertEquals(1, compos.size)
        val compo = compos.first()
        assertEquals("4k intro", compo.name)
        assertEquals("Max 4096 bytes", compo.rules)
        assertEquals(false, compo.visible)
        assertEquals(true, compo.requireFile)
        assertEquals(listOf(FileFormat.zip), compo.fileFormats)
        assertEquals(true, compo.hideAuthor)
        assertEquals(30, compo.changeoverSec)
        assertEquals(90, compo.defaultSlotSec)

        val events = services.events.getAll().getOrNull()!!
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("4k deadline", event.name)
        // dayOffset 1 lands on the importing instance's party dates.
        assertEquals(LocalDateTime(2026, 7, 16, 12, 0).toInstant(tz), event.startTime)

        val actions = services.triggers.getTriggersForSignal(event.signal()).getOrNull()!!
            .map { it.getAction().getOrNull()!! }
        assertEquals(2, actions.size)
        assertTrue(
            actions.any { it is OpenCloseSubmitting && it.compoId == compo.id && !it.open },
            "Trigger must point at the newly created compo: $actions"
        )
        assertTrue(actions.any { it is CloseVotingForAllCompos }, "actions: $actions")

        // Importing the same template again flags everything as existing and creates nothing.
        val payload2 = it.post("/admin/settings/import", templateFilePart(json)) {
            findFirst("input[name=compos]") { attribute("disabled").toBe("disabled") }
            findAll("label") {
                assertTrue(any { it.text.contains("already exists") }, "Duplicates must be flagged")
            }
            findFirst("input[name=payload]") { attribute("value") }
        }

        it.post("/admin/settings/import/confirm", formData {
            append("payload", payload2)
            append("compos", "0")
            append("events", "0")
        }) {
            findFirst("h1") { text.toBe("Party template imported") }
        }

        assertEquals(1, services.compos.getAllCompos().getOrNull()!!.size)
        assertEquals(1, services.events.getAll().getOrNull()!!.size)
    }

    @Test
    fun testWizardImportCompletesWizard() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                settings.wizardCompleted.set(false).bind()
            }
        }
        it.login("admin")

        it.post("/wizard", formData {
            append("resultsFileHeader", "")
            append("colorScheme", "Blue")
            append("timeZone", "Europe/Helsinki")
            append("partyStartDate", "2026-07-15")
            append("partyDays", "3")
        }) {
            it.redirectsTo("/wizard/import")
        }

        val json = TemplateJson.encodeToString(testTemplate())
        val payload = it.post("/wizard/import", templateFilePart(json)) {
            findFirst("input[name=payload]") { attribute("value") }
        }

        it.post("/wizard/import/confirm", formData {
            append("payload", payload)
            append("generalRules", "on")
            append("compos", "0")
            append("events", "0")
        }) {
            findFirst("h1") { text.toBe("Party template imported") }
        }

        assertEquals(true, app!!.settings.wizardCompleted.get().getOrNull())
        assertEquals(1, app!!.compos.getAllCompos().getOrNull()!!.size)

        // The wizard is done; normal admin pages are reachable.
        it.get("/admin/settings", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Settings") }
        }
    }

    @Test
    fun testImportRejectsInvalidFile() = test {
        setupServices {
            either {
                addTestAdmin(this@setupServices).bind()
                settings.partyStartDate.set(LocalDate(2026, 7, 15)).bind()
            }
        }
        it.login("admin")

        // Invalid JSON re-renders the settings page with an error on the upload form.
        it.post("/admin/settings/import", templateFilePart("this is not json")) {
            findFirst("h1") { text.toBe("Settings") }
            findAll("small.error") {
                assertTrue(any { it.text.contains("Not a valid party template") }, "Expected a parse error")
            }
        }

        // Unsupported version is rejected too.
        it.post("/admin/settings/import", templateFilePart("""{"partyboiTemplate": 999}""")) {
            findFirst("h1") { text.toBe("Settings") }
            findAll("small.error") {
                assertTrue(any { it.text.contains("Unsupported template version") }, "Expected a version error")
            }
        }
    }
}
