package party.jml.partyboi.screen

import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import party.jml.partyboi.AppServices

object ScreenPage {
    fun renderContent(slide: Slide<*>, app: AppServices) =
        createHTML().article(
            classes = classes(slide)
        ) {
            val article = this
            runBlocking { slide.render(article, app) }
        }

    fun render(slide: Slide<*>, theme: ScreenTheme, app: AppServices) =
        createHTML().html {
            head {
                title { +"Screen" }
                link(rel = "stylesheet", href = "${theme.assetsRoute}/screen.css", type = "text/css")
            }
            body {
                main(classes = "shown") {
                    attributes["id"] = "screen1"
                    article(classes = classes(slide)) {
                        val article = this
                        runBlocking { slide.render(article, app) }
                    }
                }
                main {
                    attributes["id"] = "screen2"
                }
                script(src = "/assets/screen/screen.js") {}
            }
        }

    private fun classes(slide: Slide<*>): String =
        listOfNotNull(
            slide::class.simpleName,
            slide.variant()
        ).joinToString(" ")
}
