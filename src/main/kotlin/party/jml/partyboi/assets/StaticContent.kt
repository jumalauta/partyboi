package party.jml.partyboi.assets

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import party.jml.partyboi.Config

fun Application.configureStaticContent() {
    routing {
        staticFiles("/assets/uploaded", Config.getAssetsDir().toFile())
        staticResources("/assets", "assets")
    }
}