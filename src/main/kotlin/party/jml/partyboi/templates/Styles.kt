package party.jml.partyboi.templates

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.*

fun Application.configureStyles() {

    routing {
        get("/theme.css") {
            call.respondCss {
                rule("small.error") {
                    color = Color("#D93526")
                }
                rule("section.error") {
                    color = Color("#D93526")
                }
                // Theme variables here
            }
        }
    }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
