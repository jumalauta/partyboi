package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import party.jml.partyboi.AppServices
import party.jml.partyboi.screen.SlideSetRow
import party.jml.partyboi.screen.slides.QrCodeSlide
import party.jml.partyboi.screen.slides.TextSlide
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminScreenTest : PartyboiTester {
    @Test
    fun testAdminScreenPageRequiresAdmin() = test {
        setupServices { either { addTestUser(this@setupServices).bind() } }
        it.login()
        it.get("/admin/screen/default", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
    }

    @Test
    fun testAdminScreenPageLoads() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        it.get("/admin/screen/default", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Default") }
        }
    }

    // GETting the new-text-slide form shows the create form without creating a slide;
    // only POSTing the populated form persists it, visible by default.
    @Test
    fun testCreateTextSlideDefersUntilSubmit() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }
        it.login("admin")

        it.get("/admin/screen/default/new/textslide", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("New slide / Default") }
        }
        assertEquals(0, app!!.screen.getSlideSet(SlideSetRow.DEFAULT).getOrNull()!!.size)

        it.post("/admin/screen/default/new/textslide", formData {
            append("title", "Hello")
            append("content", "World")
        }) { _ -> }

        val rows = app!!.screen.getSlideSet(SlideSetRow.DEFAULT).getOrNull()!!
        assertEquals(1, rows.size)
        val slide = rows[0].getSlide() as TextSlide
        assertEquals("Hello", slide.title)
        assertEquals("World", slide.content)
        assertEquals(true, rows[0].visible)
    }

    // Same flow for QR-code slides.
    @Test
    fun testCreateQrCodeSlideDefersUntilSubmit() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }
        it.login("admin")

        it.get("/admin/screen/default/new/qrcodeslide", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("New slide / Default") }
        }
        assertEquals(0, app!!.screen.getSlideSet(SlideSetRow.DEFAULT).getOrNull()!!.size)

        it.post("/admin/screen/default/new/qrcodeslide", formData {
            append("title", "Wifi")
            append("qrcode", "https://example.com")
            append("description", "Scan me")
        }) { _ -> }

        val rows = app!!.screen.getSlideSet(SlideSetRow.DEFAULT).getOrNull()!!
        assertEquals(1, rows.size)
        val slide = rows[0].getSlide() as QrCodeSlide
        assertEquals("Wifi", slide.title)
        assertEquals("https://example.com", slide.qrcode)
        assertEquals("Scan me", slide.description)
        assertEquals(true, rows[0].visible)
    }
}