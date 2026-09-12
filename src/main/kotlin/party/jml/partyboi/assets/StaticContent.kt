package party.jml.partyboi.assets

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.config
import party.jml.partyboi.data.NotFound
import party.jml.partyboi.templates.respondPage

fun Application.configureStaticContent() {
    val uploadedAssetsDir = config().assetsDir.toAbsolutePath().normalize()
    routing {
        get("/assets/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/")
                ?: return@get call.respondPage(NotFound("File not found"))

            // Routing URL-decodes each segment without stripping dot-segments, so the request can
            // contain ".." — resolve and verify containment before touching the filesystem.
            val target = runCatching { uploadedAssetsDir.resolve(path).normalize() }.getOrNull()
            if (target == null || target == uploadedAssetsDir || !target.startsWith(uploadedAssetsDir)) {
                return@get call.respondPage(NotFound("File not found"))
            }

            val assetFile = target.toFile()
            if (assetFile.exists() && assetFile.isFile) {
                call.applyAggressiveCaching()
                call.respondFile(assetFile)
            } else {
                val safePath = uploadedAssetsDir.relativize(target).joinToString("/")
                this::class.java.classLoader.getResourceAsStream("assets/$safePath")
                    ?.let {
                        call.applyAggressiveCaching()
                        call.respondBytes(it.readBytes(), ContentType.defaultForFile(assetFile))
                    }
                    ?: call.respondPage(NotFound("File not found"))
            }
        }
    }
}

private val AGGRESSIVE_CACHE = CacheControl.MaxAge(
    maxAgeSeconds = 31536000,
    proxyMaxAgeSeconds = 600,
    visibility = CacheControl.Visibility.Public
)

private fun ApplicationCall.applyAggressiveCaching() {
    response.header(HttpHeaders.CacheControl, AGGRESSIVE_CACHE.toString())
}