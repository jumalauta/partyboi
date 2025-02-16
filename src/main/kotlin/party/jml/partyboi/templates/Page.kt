package party.jml.partyboi.templates

import io.ktor.http.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import party.jml.partyboi.Config
import party.jml.partyboi.auth.User
import party.jml.partyboi.templates.components.icon

data class Page(
    val title: String,
    val subLinks: List<NavItem> = emptyList(),
    val children: DIV.() -> Unit,
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
                title { +"$titleText - ${Config.get().instanceName}" }
                link(rel = "stylesheet", href = "/assets/pico.min.css", type = "text/css")
                link(rel = "stylesheet", href = "/assets/fontawesome.min.css", type = "text/css")
                link(rel = "stylesheet", href = "/assets/solid.min.css", type = "text/css")
                link(rel = "stylesheet", href = "/assets/partyboi.css", type = "text/css")
            }
            body {
                main(classes = "container") {
                    header {
                        nav {
                            ul {
                                li {
                                    button(classes = "mobile-nav-button flat-button") {
                                        span {
                                            icon("bars")
                                        }
                                    }
                                    strong { a(href = "/") { +Config.get().instanceName } }
                                }
                            }
                            ul {
                                if (user == null) {
                                    renderItems(path, Navigation.guestItems, emptyList())
                                } else {
                                    navigationDropdown(path, user.name, Navigation.accountItems)
                                }
                            }
                        }
                    }

                    section(classes = "nav-and-content") {
                        navigation(user, path, subLinks)
                        div(classes = "content") {
                            children()
                        }
                    }
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