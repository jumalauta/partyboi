package party.jml.partyboi.templates

import io.ktor.http.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

data class Page(
    val title: String,
    val body: BODY.() -> Unit
) : Renderable {
    override fun getHTML(): String {
        val titleText = title
        return createHTML().html {
            head {
                title { +titleText }
                link(rel = "stylesheet", href = "/styles.css", type = "text/css")
            }
            body {
                body(this)
            }
        }
    }
}

class RedirectPage(val location: String) : Renderable {
    override fun getHTML(): String {
        return ""
    }

    override fun statusCode(): HttpStatusCode {
        return HttpStatusCode.Found
    }

    override fun headers(): Map<String, String> {
        return mapOf("Location" to location)
    }
}
