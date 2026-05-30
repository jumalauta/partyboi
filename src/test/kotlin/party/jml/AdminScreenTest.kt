package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.infoscreen.slides.ImageSlide
import party.jml.partyboi.infoscreen.slides.QrCodeSlide
import party.jml.partyboi.infoscreen.slides.TextSlide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // Image picker: the GET lists every image asset that's not already referenced by
    // an ImageSlide in this slide set; assets used elsewhere are excluded.
    @Test
    fun testImagePickerListsUnusedImages() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either {
                addTestAdmin(this@setupServices).bind()
                assets.write(FileUpload.fromByteArray("x.png", ByteArray(8))).bind()
                assets.write(FileUpload.fromByteArray("y.png", ByteArray(8))).bind()
                screen.addSlide("default", ImageSlide("x.png"), makeVisible = true).bind()
            }
        }
        it.login("admin")

        val body = it.client.get("/admin/screen/default/new/imageslide").bodyAsText()
        assertTrue("y.png" in body, "Expected unused image y.png in picker")
        assertTrue("x.png" !in body, "Expected used image x.png to be excluded")
    }

    // POSTing the picker creates one visible ImageSlide per checked assetImage value.
    @Test
    fun testImagePickerCreatesSlidesForSelectedImages() = test {
        var app: AppServices? = null
        setupServices {
            app = this
            either { addTestAdmin(this@setupServices).bind() }
        }
        it.login("admin")

        it.client.submitForm("/admin/screen/default/new/imageslide", parameters {
            append("assetImage", "a.png")
            append("assetImage", "b.jpg")
        })

        val rows = app!!.screen.getSlideSet(SlideSetRow.DEFAULT).getOrNull()!!
        val slides = rows.map { it.getSlide() }.filterIsInstance<ImageSlide>()
        assertEquals(setOf("a.png", "b.jpg"), slides.map { it.assetImage }.toSet())
        assertTrue(rows.all { it.visible }, "All created image slides should be visible")
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