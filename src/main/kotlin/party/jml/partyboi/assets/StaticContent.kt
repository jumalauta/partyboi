package party.jml.partyboi.assets

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import party.jml.partyboi.config

fun Application.configureStaticContent() {
    val uploadedAssetsDir = config().assetsDir.toFile()
    routing {
        staticFiles("/assets/uploaded", uploadedAssetsDir)
        staticResources("/assets", "assets") {
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
        staticResources("/", "favicon")
    }
}