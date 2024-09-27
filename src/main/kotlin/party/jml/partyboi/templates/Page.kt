package party.jml.partyboi.templates

import io.ktor.http.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

data class Page(
    val title: String,
    val children: MAIN.() -> Unit
) : Renderable {
    override fun getHTML(): String {
        val titleText = title
        return createHTML().html {
            attributes.put("data-theme", "light")
            head {
                title { +titleText }
                link(rel = "stylesheet", href = "/assets/pico.min.css", type = "text/css")
                link(rel = "stylesheet", href = "/theme.css", type = "text/css")
            }
            body {
                main(classes = "container") {
                    nav {
                        ul {
                            li { strong { +"PartyBoi" } }
                        }
                        ul {
                            li { a(href="/submit") { +"Submit entries" } }
                            li { a(href="/vote") { +"Vote" } }
                        }
                    }

                    children(this)

                    footer {
                        small {
                            strong { +"PartyBoi" }
                            +" v.0.1 by Naetti tyttoe/jML"
                        }
                    }
                }
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
