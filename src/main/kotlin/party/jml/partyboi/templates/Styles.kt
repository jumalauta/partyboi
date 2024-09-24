package party.jml.partyboi.templates

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.*

fun Application.configureStyles() {

    routing {
        get("/styles.css") {
            call.respondCss {
                body {
                    rule("form.appForm") {
                        label {
                            display = Display.block
                            marginBottom = LinearDimension("10px")

                            span {
                                display = Display.block
                                fontSize = LinearDimension("80%")
                                paddingLeft = LinearDimension("5px")
                            }
                            input {
                                padding = "5px"
                                minWidth = LinearDimension("50vw")
                                borderWidth = LinearDimension("1px")
                                borderColor = Color.darkGray
                                borderRadius = LinearDimension("3px")
                            }
                            select {
                                minWidth = LinearDimension("50vw")
                            }
                        }
                        footer {
                            padding = "5px"
                        }
                    }

                    rule(".error") {
                        color = Color.red
                    }
                }
            }
        }
    }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
