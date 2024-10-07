package party.jml.partyboi.screen

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import party.jml.partyboi.Screen

object ScreenPage {
    fun renderContent(screen: Screen) =
        createHTML().article { screenBody(screen) }

    fun render(screen: Screen) =
        createHTML().html {
            head {
                title { +"Screen" }
                link(rel = "stylesheet", href = "/assets/screen/screen.css", type = "text/css")
            }
            body {
                main(classes = "shown") {
                    attributes["id"] = "screen1"
                    article {
                        screenBody(screen)
                    }
                }
                main {
                    attributes["id"] = "screen2"
                }
                script(src = "/assets/screen/screen.js") {}
            }
        }
}

fun FlowContent.screenBody(screen: Screen) {
    h1 { +screen.title }
    p { +screen.content }
}