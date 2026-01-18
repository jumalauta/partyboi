package party.jml.partyboi.system

import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import java.net.URI

fun RoutingRequest.fullHost(): URI {
    val origin = call.request.origin
    val portPart = when {
        (origin.scheme == "http" && origin.serverPort == 80) ||
                (origin.scheme == "https" && origin.serverPort == 443) -> ""

        else -> ":${origin.serverPort}"
    }

    return URI.create("${origin.scheme}://${origin.serverHost}$portPart")
}
