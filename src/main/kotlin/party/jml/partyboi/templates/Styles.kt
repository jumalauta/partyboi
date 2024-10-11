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
                rule(".error") {
                    color = Color("#D93526")
                }
                rule(".space-between") {
                    display = Display.flex
                    justifyContent = JustifyContent.spaceBetween
                }
                rule("summary > header") {
                    display = Display.inlineFlex
                    justifyContent = JustifyContent.spaceBetween
                    width = LinearDimension("calc(100% - 4ex)")
                }
                // Theme variables here
            }
        }
    }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
