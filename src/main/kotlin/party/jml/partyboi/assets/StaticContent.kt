package party.jml.partyboi.assets

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.config
import party.jml.partyboi.data.NotFound
import party.jml.partyboi.templates.respondPage
import java.io.File

fun Application.configureStaticContent() {
    val uploadedAssetsDir = config().assetsDir.toFile()
    routing {
        get("/assets/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/")
                ?: return@get call.respondPage(NotFound("File not found"))

            val assetFile = File("$uploadedAssetsDir/$path")
            if (assetFile.exists() && assetFile.isFile) {
                call.respondFile(assetFile)
            } else {
                val resourceStream = this::class.java.classLoader.getResourceAsStream("assets/$path")
                if (resourceStream != null) {
                    call.respondBytes(resourceStream.readBytes(), ContentType.defaultForFile(assetFile))
                } else {
                    call.respondPage(NotFound("File not found"))
                }
            }
        }

        staticResources("/", "favicon") {
            aggressiveCaching()
        }
    }
}

fun StaticContentConfig<*>.aggressiveCaching() {
    cacheControl {
        listOf(
            CacheControl.MaxAge(
                maxAgeSeconds = 31536000,
                proxyMaxAgeSeconds = 600,
                visibility = CacheControl.Visibility.Public
            )
        )
    }
}