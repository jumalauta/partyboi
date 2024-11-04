package party.jml.partyboi.templates

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.*
import kotlinx.css.properties.*

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
                rule("button.flat-button") {
                    color = Color.inherit
                    background = "none"
                    border = "none"
                    padding = "0"
                    lineHeight = LineHeight.inherit
                    fontSize = LinearDimension.inherit
                    marginRight = LinearDimension("1ex")
                    minWidth = LinearDimension("2.5ex")
                }
                rule("button.flat-button.off > *") {
                    opacity = 0.25
                }
                rule(".align-right") {
                    textAlign = TextAlign.right
                }
                rule(".narrow") {
                    width = LinearDimension("20px")
                }
                rule(".no-margin") {
                    margin = "0"
                }
                rule("img.full-width") {
                    width = LinearDimension("100%")
                }
                rule("nav a.active") {
                    fontWeight = FontWeight.bold
                }
                rule(".info-qr-code") {
                    maxHeight = LinearDimension("30vh")
                }
                media("(min-width: 1280px)") {
                    rule(".two-columns") {
                        display = Display.flex
                        gap = Gap("20px")
                        width = LinearDimension("100%")
                    }
                    rule(".two-columns-left") {
                        flexGrow = 1.0
                        maxWidth = LinearDimension("50%")
                    }
                    rule(".two-columns-right") {
                        flexGrow = 1.0
                        maxWidth = LinearDimension("50%")
                    }
                }
                // Theme variables here
            }
        }
    }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
