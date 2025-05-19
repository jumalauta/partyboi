package party.jml.partyboi.screen

import kotlinx.html.*
import kotlinx.html.stream.createHTML

object ScreenPage {
    fun renderContent(slide: Slide<*>) =
        createHTML().article(
            classes = classes(slide)
        ) {
            slide.render(this)
        }

    fun render(slide: Slide<*>, theme: ScreenTheme) =
        createHTML().html {
            head {
                title { +"Screen" }
                link(rel = "stylesheet", href = "/assets/screen/${theme.dir}/screen.css", type = "text/css")
            }
            body {
                main(classes = "shown") {
                    attributes["id"] = "screen1"
                    article(classes = classes(slide)) { slide.render(this) }
                }
                main {
                    attributes["id"] = "screen2"
                }
                script(src = "/assets/screen/${theme.dir}/screen.js") {}
            }
        }

    private fun classes(slide: Slide<*>): String =
        listOfNotNull(
            slide::class.simpleName,
            slide.variant()
        ).joinToString(" ")
}
