package party.jml.partyboi.signals

import arrow.core.left
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterString

fun Application.configureSignalRouting(app: AppServices) {
    routing {
        get("/signals/{type}") {
            either {
                val type = call.parameterString("type").bind()
                SignalType.fromString(type).bind()
            }.fold(
                { call.apiRespond { it.left() } },
                { type -> app.signals.waitFor(type) { signal -> call.respondText(signal.toString()) } }
            )
        }
    }
}