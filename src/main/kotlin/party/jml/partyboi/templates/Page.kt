package party.jml.partyboi.templates

import io.ktor.http.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import party.jml.partyboi.Config
import party.jml.partyboi.auth.User

data class Page(
    val title: String,
    val children: MAIN.() -> Unit,
) : Renderable {
    override fun getHTML(user: User?, path: String): String {
        val titleText = title
        return createHTML().html {
            attributes.put("data-theme", "light")
            head {
                meta { charset = "utf-8" }
                meta {
                    name = "viewport"
                    content = "width=device-width, initial-scale=1"
                }
                title { +"$titleText - ${Config.getInstanceName()}" }
                link(rel = "stylesheet", href = "/assets/pico.min.css", type = "text/css")
                link(rel = "stylesheet", href = "/assets/fontawesome.min.css", type = "text/css")
                link(rel = "stylesheet", href = "/assets/solid.min.css", type = "text/css")
                link(rel = "stylesheet", href = "/theme.css", type = "text/css")
            }
            body {
                main(classes = "container") {
                    navigation(user, path)
                    children()
                }
                script(src = "/assets/partyboi.js") {}
            }
        }
    }
}

class Redirection(val location: String) : Renderable {
    override fun getHTML(user: User?, path: String): String {
        return ""
    }

    override fun statusCode(): HttpStatusCode {
        return HttpStatusCode.Found
    }

    override fun headers(): Map<String, String> {
        return mapOf("Location" to location)
    }
}

class EmptyPage() : Renderable {
    override fun getHTML(user: User?, path: String): String {
        return ""
    }

    override fun statusCode(): HttpStatusCode {
        return HttpStatusCode.NoContent
    }
}