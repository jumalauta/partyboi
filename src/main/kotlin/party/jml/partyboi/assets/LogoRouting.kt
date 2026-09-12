package party.jml.partyboi.assets

import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import party.jml.partyboi.AppServices
import party.jml.partyboi.BuildInfo
import party.jml.partyboi.Config
import party.jml.partyboi.data.NotFound
import party.jml.partyboi.data.catchError
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.respondPage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

const val LOGO_ASSET = "logo.svg"

// Favicon paths follow the conventional names browsers and platforms probe for.
private val FAVICON_SIZES = mapOf(
    "favicon-16x16.png" to 16,
    "favicon-32x32.png" to 32,
    "apple-touch-icon.png" to 180,
    "android-chrome-192x192.png" to 192,
    "android-chrome-512x512.png" to 512,
)

fun Application.configureLogoRouting(app: AppServices) {
    val pngCache = LogoPngCache()

    suspend fun RoutingContext.respondPng(size: Int) {
        either {
            val logo = loadLogo(app).bind()
            if (!respondNotModified(call, etagOf(logo.version, "png", size.toString()))) {
                val png = pngCache.get(logo.version, size) { rasterizeToPng(logo.svg, size) }.bind()
                call.respondBytes(png, ContentType.Image.PNG)
            }
        }.getOrElse { call.respondPage(it) }
    }

    routing {
        get("/logo.svg") {
            either {
                val logo = loadLogo(app).bind()
                if (!respondNotModified(call, etagOf(logo.version, "svg"))) {
                    call.respondText(logo.svg, ContentType.Image.SVG)
                }
            }.getOrElse { call.respondPage(it) }
        }

        FAVICON_SIZES.forEach { (path, size) ->
            get("/$path") { respondPng(size) }
        }

        // Legacy clients blind-request this path; they sniff content, so PNG bytes are fine.
        get("/favicon.ico") { respondPng(32) }

        get("/site.webmanifest") {
            either {
                val hex = app.settings.getTheme().bind().colorScheme.hex
                val instanceName = Config.get().instanceName
                val body = Json.encodeToString(
                    WebManifest(
                        name = instanceName,
                        shortName = instanceName,
                        icons = listOf(
                            ManifestIcon("/android-chrome-192x192.png", "192x192", "image/png"),
                            ManifestIcon("/android-chrome-512x512.png", "512x512", "image/png"),
                        ),
                        themeColor = hex,
                    )
                )
                if (!respondNotModified(call, etagOf(body))) {
                    call.respondText(body, ContentType("application", "manifest+json"))
                }
            }.getOrElse { call.respondPage(it) }
        }
    }
}

/**
 * The logo SVG to serve, plus a version string that changes whenever the served
 * bytes would change (theme color, logo upload/removal, new build) — used as the
 * ETag source and the PNG cache key.
 */
data class ThemedLogo(val svg: String, val version: String)

/**
 * An instance-uploaded logo is a deliberate customization (possibly multi-color) and is
 * served verbatim; only the bundled default logo is recolored to the theme's primary color.
 */
suspend fun loadLogo(app: AppServices): AppResult<ThemedLogo> = either {
    if (app.assets.exists(LOGO_ASSET)) {
        val svg = catchError { app.assets.getFile(LOGO_ASSET).toFile().readText() }.bind()
        val checksum = app.assets.getChecksum(LOGO_ASSET).bind()
        ThemedLogo(svg, "custom|$checksum")
    } else {
        val svg = BuildInfo::class.java.classLoader.getResourceAsStream("assets/$LOGO_ASSET")
            ?.readBytes()?.toString(Charsets.UTF_8)
            ?: raise(NotFound("Logo not found"))
        val hex = app.settings.getTheme().bind().colorScheme.hex
        ThemedLogo(recolorSvg(svg, hex), "default|$hex|${BuildInfo.timestamp}")
    }
}

private val STYLE_COLOR = Regex("""(fill|stroke)\s*:\s*#[0-9a-fA-F]{3,8}""")
private val ATTR_COLOR = Regex("""(fill|stroke)\s*=\s*(["'])#[0-9a-fA-F]{3,8}\2""")

/**
 * Replaces every literal hex color in fill/stroke CSS declarations and XML attributes.
 * `none`, `currentColor`, named colors and gradient references are untouched, so cutouts
 * in the logo survive recoloring.
 */
fun recolorSvg(svg: String, hex: String): String =
    svg.replace(STYLE_COLOR) { "${it.groupValues[1]}:$hex" }
        .replace(ATTR_COLOR) { "${it.groupValues[1]}=${it.groupValues[2]}$hex${it.groupValues[2]}" }

fun rasterizeToPng(svg: String, size: Int): AppResult<ByteArray> = catchError {
    val transcoder = PNGTranscoder()
    transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, size.toFloat())
    transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, size.toFloat())
    val output = ByteArrayOutputStream()
    transcoder.transcode(TranscoderInput(svg.byteInputStream()), TranscoderOutput(output))
    output.toByteArray()
}

/**
 * Caches rasterized PNGs for the current logo version. Rasterization only happens once
 * per size; a theme color change or logo upload changes the version, which clears the cache.
 */
class LogoPngCache {
    @Volatile
    private var version: String? = null
    private val bySize = ConcurrentHashMap<Int, ByteArray>()

    fun get(currentVersion: String, size: Int, render: () -> AppResult<ByteArray>): AppResult<ByteArray> {
        if (version != currentVersion) {
            synchronized(this) {
                if (version != currentVersion) {
                    bySize.clear()
                    version = currentVersion
                }
            }
        }
        bySize[size]?.let { return it.right() }
        return render().onRight { bySize[size] = it }
    }
}

private fun etagOf(vararg parts: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(parts.joinToString("|").toByteArray())
    return "\"" + digest.joinToString("") { "%02x".format(it) } + "\""
}

/**
 * Sets revalidation headers and answers 304 if the client already has this version.
 * Returns true when the 304 was sent (the caller must not respond further).
 */
private suspend fun respondNotModified(call: ApplicationCall, etag: String): Boolean {
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.response.header(HttpHeaders.ETag, etag)
    return if (call.request.header(HttpHeaders.IfNoneMatch) == etag) {
        call.respond(HttpStatusCode.NotModified)
        true
    } else {
        false
    }
}

@Serializable
private data class ManifestIcon(val src: String, val sizes: String, val type: String)

@Serializable
private data class WebManifest(
    val name: String,
    @SerialName("short_name") val shortName: String,
    val icons: List<ManifestIcon>,
    @SerialName("theme_color") val themeColor: String,
    @SerialName("background_color") val backgroundColor: String = "#ffffff",
    val display: String = "standalone",
)
