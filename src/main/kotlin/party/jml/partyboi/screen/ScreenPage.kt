package party.jml.partyboi.screen

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import party.jml.partyboi.AppServices

object ScreenPage {
    fun renderContent(slide: Slide<*>, app: AppServices) =
        createHTML().article(
            classes = classes(slide)
        ) {
            slide.render(this, app)
        }

    fun render(slide: Slide<*>, theme: ScreenTheme, app: AppServices) =
        createHTML().html {
            head {
                title { +"Screen" }
                link(rel = "stylesheet", href = "/assets/screen/${theme.dir}/screen.css", type = "text/css")
            }
            body {
                main(classes = "shown") {
                    attributes["id"] = "screen1"
                    article(classes = classes(slide)) { slide.render(this, app) }
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
