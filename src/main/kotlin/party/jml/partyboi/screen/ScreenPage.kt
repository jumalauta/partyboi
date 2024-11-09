package party.jml.partyboi.screen

import kotlinx.html.*
import kotlinx.html.stream.createHTML

object ScreenPage {
    fun renderContent(slide: Slide<*>) =
        createHTML().article(classes = slide::class.simpleName) {
            slide.render(this)
        }

    fun render(slide: Slide<*>) =
        createHTML().html {
            head {
                title { +"Screen" }
                link(rel = "stylesheet", href = "/assets/screen/screen.css", type = "text/css")
            }
            body {
                main(classes = "shown") {
                    attributes["id"] = "screen1"
                    article(classes = slide::class.simpleName) { slide.render(this) }
                }
                main {
                    attributes["id"] = "screen2"
                }
                script(src = "/assets/screen/screen.js") {}
            }
        }
}
