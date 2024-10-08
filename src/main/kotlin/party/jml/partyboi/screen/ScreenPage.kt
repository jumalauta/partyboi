package party.jml.partyboi.screen

import kotlinx.html.*
import kotlinx.html.stream.createHTML

object ScreenPage {
    fun renderContent(screen: Screen) =
        createHTML().article { screen.render(this) }

    fun render(screen: Screen) =
        createHTML().html {
            head {
                title { +"Screen" }
                link(rel = "stylesheet", href = "/assets/screen/screen.css", type = "text/css")
            }
            body {
                main(classes = "shown") {
                    attributes["id"] = "screen1"
                    article { screen.render(this) }
                }
                main {
                    attributes["id"] = "screen2"
                }
                script(src = "/assets/screen/screen.js") {}
            }
        }
}
