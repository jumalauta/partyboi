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
import party.jml.partyboi.infoscreen.slides.QrCodeSlide
import party.jml.partyboi.infoscreen.slides.ScheduleSlide
import party.jml.partyboi.infoscreen.slides.TextSlide
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
        slideSets = listOf(
            TemplateSlideSet(
                id = "info",
                name = "Info",
                maxImageSlides = 4,
                slides = listOf(
                    TemplateTextSlide("Welcome", "Hello"),
                    TemplateQrCodeSlide("Website", "https://example.org", "Visit us"),
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
                screen.setMaxImageSlides("default", 5).bind()
                screen.addSlide("default", TextSlide("Welcome", "Hello"), makeVisible = true).bind()
                screen.addSlide("default", QrCodeSlide("Website", "https://example.org", "Visit us")).bind()
                // Schedule slides are never exported.
                screen.addSlide("default", ScheduleSlide(LocalDate(2025, 8, 1)), makeVisible = true).bind()
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
            append("slideSetIds", "default")
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

        // Text and QR code slides are exported; the schedule slide is left out.
        assertEquals(1, template.slideSets.size)
        val slideSet = template.slideSets.first()
        assertEquals("default", slideSet.id)
        assertEquals(5, slideSet.maxImageSlides)
        assertEquals(
            listOf(
                TemplateTextSlide("Welcome", "Hello", visible = true),
                TemplateQrCodeSlide("Website", "https://example.org", "Visit us", visible = false),
            ),
            slideSet.slides,
        )
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
        // Event times must be previewed in the template's timezone (Helsinki 12:00),
        // not the instance's current UTC.
        val payload = it.post("/admin/settings/import", templateFilePart(json)) {
            findAll("label") {
                assertTrue(any { it.text.contains("12:00") }, "Preview must show the template-timezone time")
            }
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
            append("slideSets", "0")
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

        // The imported public event gets its generated schedule slide.
        val scheduleDates = services.screen.getSlideSet("default").getOrNull()!!
            .mapNotNull { (it.getSlide() as? ScheduleSlide)?.date }
        assertEquals(listOf(LocalDate(2026, 7, 16)), scheduleDates)

        // The slide set was created with its text and QR code slides and its image
        // slide throttle setting.
        val infoSet = services.screen.getSlideSets().getOrNull()!!.find { it.id == "info" }
        assertEquals(4, infoSet?.maxImageSlides)
        val slides = services.screen.getSlideSet("info").getOrNull()!!.map { it.getSlide() }
        assertEquals(2, slides.size)
        assertTrue(slides.any { it is TextSlide && it.title == "Welcome" && it.content == "Hello" })
        assertTrue(slides.any { it is QrCodeSlide && it.title == "Website" && it.qrcode == "https://example.org" })

        // Importing the same template again flags everything as existing and creates nothing.
        val payload2 = it.post("/admin/settings/import", templateFilePart(json)) {
            findFirst("input[name=compos]") { attribute("disabled").toBe("disabled") }
            findAll("label") {
                assertTrue(any { it.text.contains("already exists") }, "Duplicates must be flagged")
            }
            findFirst("input[name=payload]") { attribute("value") }
        }

        // Disabled checkboxes are not submitted by real browsers, so the confirm POST
        // carries no compo/event indexes — the report must still list the skips.
        it.post("/admin/settings/import/confirm", formData {
            append("payload", payload2)
        }) {
            findFirst("h1") { text.toBe("Party template imported") }
            findAll("li") {
                assertTrue(any { it.text.contains("Compos skipped") }, "Report must list skipped compos")
                assertTrue(any { it.text.contains("Events skipped") }, "Report must list skipped events")
                assertTrue(any { it.text.contains("slides skipped") }, "Report must list skipped slides")
            }
        }

        assertEquals(1, services.compos.getAllCompos().getOrNull()!!.size)
        assertEquals(1, services.events.getAll().getOrNull()!!.size)
        assertEquals(2, services.screen.getSlideSet("info").getOrNull()!!.size)
        assertEquals(
            1,
            services.screen.getSlideSet("default").getOrNull()!!.count { it.getSlide() is ScheduleSlide },
            "Re-import must not duplicate schedule slides"
        )
    }

    @Test
    fun testImportDoesNotDuplicateSameNamedItemsWithinTemplate() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                time.timeZone.set(tz).bind()
                settings.partyStartDate.set(LocalDate(2026, 7, 15)).bind()
            }
        }
        it.login("admin")

        val template = PartyTemplate(
            partyboiTemplate = PARTY_TEMPLATE_VERSION,
            compos = listOf(TemplateCompo(name = "Demo"), TemplateCompo(name = "demo ")),
            events = listOf(
                TemplateEvent(name = "Deadline", start = RelativeTime(1, "12:00")),
                TemplateEvent(name = "deadline", start = RelativeTime(1, "18:00")),
            ),
        )
        val payload = it.post("/admin/settings/import", templateFilePart(TemplateJson.encodeToString(template))) {
            findFirst("input[name=payload]") { attribute("value") }
        }

        it.post("/admin/settings/import/confirm", formData {
            append("payload", payload)
            append("compos", "0")
            append("compos", "1")
            append("events", "0")
            append("events", "1")
        }) {
            findFirst("h1") { text.toBe("Party template imported") }
        }

        assertEquals(1, app!!.compos.getAllCompos().getOrNull()!!.size)
        assertEquals(1, app!!.events.getAll().getOrNull()!!.size)
    }

    @Test
    fun testImportWithoutPartyStartDateShowsError() = test {
        // setupServices leaves partyStartDate unset.
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")

        it.post("/admin/settings/import", templateFilePart(TemplateJson.encodeToString(testTemplate()))) {
            findFirst("h1") { text.toBe("Settings") }
            findAll("section.error") {
                assertTrue(
                    any { it.text.contains("party start date") },
                    "The missing start date must be surfaced on the upload form"
                )
            }
        }
    }

    @Test
    fun testFailedWizardUploadDoesNotPersistStartDate() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                settings.wizardCompleted.set(false).bind()
            }
        }
        it.login("admin")

        it.post("/wizard/import", formData {
            append("partyStartDate", "2062-07-15")
            append("file", "this is not json".toByteArray(), headers {
                append("Content-Type", "application/json")
                append("Content-Disposition", "form-data; name=\"file\"; filename=\"template.json\"")
            })
        }) {
            findAll("small.error") {
                assertTrue(any { it.text.contains("Not a valid party template") }, "Expected a parse error")
            }
        }

        assertEquals(null, app!!.settings.partyStartDate.get().getOrNull())
    }

    @Test
    fun testWizardImportThenSettingsCompletesWizard() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                settings.wizardCompleted.set(false).bind()
            }
        }
        it.login("admin")

        // The import step (step 1) takes the party start date together with the file,
        // so the imported schedule can be placed on the party days.
        val json = TemplateJson.encodeToString(testTemplate())
        val payload = it.post("/wizard/import", formData {
            append("partyStartDate", "2026-07-15")
            append("file", json.toByteArray(), headers {
                append("Content-Type", "application/json")
                append("Content-Disposition", "form-data; name=\"file\"; filename=\"template.json\"")
            })
        }) {
            findFirst("input[name=payload]") { attribute("value") }
        }

        it.post("/wizard/import/confirm", formData {
            append("payload", payload)
            append("generalRules", "on")
            append("partyDays", "on")
            append("timeZone", "on")
            append("compos", "0")
            append("events", "0")
        }) {
            findFirst("h1") { text.toBe("Party template imported") }
        }

        // The import is done but the wizard continues to the settings step.
        assertEquals(false, app!!.settings.wizardCompleted.get().getOrNull())
        assertEquals(LocalDate(2026, 7, 15), app!!.settings.partyStartDate.get().getOrNull())
        assertEquals(1, app!!.compos.getAllCompos().getOrNull()!!.size)
        val event = app!!.events.getAll().getOrNull()!!.single()
        assertEquals(LocalDateTime(2026, 7, 16, 12, 0).toInstant(tz), event.startTime)

        // The settings step (step 2) is prefilled from the import and completes the wizard.
        it.get("/wizard/settings", HttpStatusCode.OK) {
            relaxed = true
            findFirst("input[name=partyStartDate]") { attribute("value").toBe("2026-07-15") }
            findFirst("input[name=partyDays]") { attribute("value").toBe("4") }
        }

        it.post("/wizard/settings", formData {
            append("resultsFileHeader", "")
            append("colorScheme", "Blue")
            append("timeZone", "Europe/Helsinki")
            append("partyStartDate", "2026-07-15")
            append("partyDays", "4")
        }) {
            it.redirectsTo("/admin/voting")
        }

        assertEquals(true, app!!.settings.wizardCompleted.get().getOrNull())

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
