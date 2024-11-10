package party.jml.partyboi.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.statuspages.*
import kotlinx.html.h1
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.errorMessage
import party.jml.partyboi.templates.respondPage

fun Application.configureDefaultRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondPage(Page("Error") {
                h1 { +"Oh noes..." }
                errorMessage(cause)
            })
        }
    }
    install(AutoHeadResponse)
}
