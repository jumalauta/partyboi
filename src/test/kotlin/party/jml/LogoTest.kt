package party.jml

import arrow.core.raise.either
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import party.jml.partyboi.AppServices
import party.jml.partyboi.assets.recolorSvg
import party.jml.partyboi.templates.ColorScheme
import kotlin.test.*

class LogoTest : PartyboiTester {
    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)

    @Test
    fun testDefaultLogoIsThemed() = test {
        setupServices { either { settings.colorScheme.set(ColorScheme.Blue).bind() } }
        val response = it.client.get("/logo.svg")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image", response.contentType()?.contentType)
        assertEquals("svg+xml", response.contentType()?.contentSubtype)
        val body = response.bodyAsText()
        assertContains(body, "fill:#0172ad")
        assertFalse(body.contains("#f20d5e"), "original logo color should be replaced")
    }

    @Test
    fun testThemeChangeRecolorsLogo() = test {
        setupServices { either { settings.colorScheme.set(ColorScheme.Red).bind() } }
        val body = it.client.get("/logo.svg").bodyAsText()
        assertContains(body, "fill:#c52f21")
    }

    @Test
    fun testUploadedLogoIsServedVerbatim() = test {
        setupServices { either { addTestAdmin(this@setupServices).bind() } }
        it.login("admin")
        val customSvg =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">""" +
                    """<rect width="10" height="10" fill="#123456"/>""" +
                    """<circle cx="5" cy="5" r="3" fill="#654321"/></svg>"""
        it.post("/admin/assets", formData {
            append("files", customSvg.toByteArray(), headers {
                append("Content-Type", "image/svg+xml")
                append("Content-Disposition", "form-data; name=\"files\"; filename=\"logo.svg\"")
            })
        }) {
            it.redirectsTo("/admin/assets")
        }

        // A custom logo is a deliberate customization: served as-is, not recolored
        assertEquals(customSvg, it.client.get("/logo.svg").bodyAsText())

        // ...but favicons are still rasterized from it
        val png = it.client.get("/favicon-32x32.png")
        assertEquals(HttpStatusCode.OK, png.status)
        assertContentEquals(pngMagic, png.body<ByteArray>().take(4).toByteArray())
    }

    @Test
    fun testFaviconPngAndEtagRevalidation() = test {
        lateinit var app: AppServices
        setupServices {
            app = this
            either { settings.colorScheme.set(ColorScheme.Blue).bind() }
        }

        val first = it.client.get("/favicon-32x32.png")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(ContentType.Image.PNG, first.contentType())
        val firstBytes = first.body<ByteArray>()
        assertContentEquals(pngMagic, firstBytes.take(4).toByteArray())

        val etag = first.headers[HttpHeaders.ETag]
        assertNotNull(etag)
        val revalidated = it.client.get("/favicon-32x32.png") {
            header(HttpHeaders.IfNoneMatch, etag)
        }
        assertEquals(HttpStatusCode.NotModified, revalidated.status)

        // Theme change invalidates both the ETag and the cached PNG bytes
        app.settings.colorScheme.set(ColorScheme.Red)
        val recolored = it.client.get("/favicon-32x32.png") {
            header(HttpHeaders.IfNoneMatch, etag)
        }
        assertEquals(HttpStatusCode.OK, recolored.status)
        assertNotEquals(etag, recolored.headers[HttpHeaders.ETag])
        assertFalse(firstBytes.contentEquals(recolored.body<ByteArray>()))
    }

    @Test
    fun testFaviconIcoServesPng() = test {
        setupServices { either { } }
        val response = it.client.get("/favicon.ico")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(pngMagic, response.body<ByteArray>().take(4).toByteArray())
    }

    @Test
    fun testWebManifest() = test {
        setupServices { either { settings.colorScheme.set(ColorScheme.Blue).bind() } }
        val response = it.client.get("/site.webmanifest")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"theme_color\":\"#0172ad\"")
        assertContains(body, "\"name\":")
        assertContains(body, "/android-chrome-192x192.png")
    }

    @Test
    fun testHeaderAndFaviconMarkup() = test {
        setupServices { either { } }
        it.get("/", HttpStatusCode.OK) {
            relaxed = true
            findFirst("header nav a.brand img") { attribute("src").toBe("/logo.svg") }
            findFirst("link[rel=icon][type='image/svg+xml']") { attribute("href").toBe("/logo.svg") }
        }
    }
}

class RecolorSvgTest {
    @Test
    fun testStyleDeclarationsAreRecolored() {
        val svg = """<path style="fill:#f20d5e;stroke:#abc;"/>"""
        assertEquals("""<path style="fill:#123456;stroke:#123456;"/>""", recolorSvg(svg, "#123456"))
    }

    @Test
    fun testAttributesAreRecolored() {
        val svg = """<rect fill="#AABBCC"/><circle stroke='#ff0000aa'/>"""
        assertEquals("""<rect fill="#123456"/><circle stroke='#123456'/>""", recolorSvg(svg, "#123456"))
    }

    @Test
    fun testNonHexValuesAreUntouched() {
        val svg = """<path style="fill:none" fill="none" stroke="currentColor"/><rect fill="url(#grad)"/>"""
        assertEquals(svg, recolorSvg(svg, "#123456"))
    }
}
