package party.jml.partyboi.templates

import io.ktor.http.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import party.jml.partyboi.BuildInfo
import party.jml.partyboi.Config
import party.jml.partyboi.auth.User
import party.jml.partyboi.messages.Message
import party.jml.partyboi.templates.components.icon

data class Page(
    val title: String,
    val subLinks: List<NavItem> = emptyList(),
    val children: DIV.() -> Unit,
) : Renderable, Themeable, Messaging {
    private var theme = Theme.Default
    private var messages: List<Message> = emptyList()

    override fun setTheme(theme: Theme) {
        this.theme = theme
    }

    override fun setMessages(messages: List<Message>) {
        this.messages = messages
    }

    override fun getContent(user: User?, path: String): String {
        val titleText = title
        return "<!DOCTYPE html>\n" + createHTML().html {
            attributes.put("data-theme", "light")
            head {
                meta { charset = "utf-8" }
                meta {
                    name = "viewport"
                    content = "width=device-width, height=device-height, initial-scale=1"
                }
                title { +"$titleText - ${Config.get().instanceName}" }
                faviconLinks()
                link(rel = "stylesheet", href = BuildInfo.asset("/assets/picocss/${theme.colorScheme.filename}"), type = "text/css")
                link(rel = "stylesheet", href = BuildInfo.asset("/assets/fontawesome.min.css"), type = "text/css")
                link(rel = "stylesheet", href = BuildInfo.asset("/assets/solid.min.css"), type = "text/css")
                link(rel = "stylesheet", href = BuildInfo.asset("/assets/partyboi.css"), type = "text/css")
            }
            body {
                main(classes = "container") {
                    header {
                        nav {
                            ul {
                                li(classes = "brand-row") {
                                    button(classes = "mobile-nav-button flat-button") {
                                        attributes["aria-label"] = "Open navigation"
                                        attributes["aria-expanded"] = "false"
                                        attributes["aria-controls"] = "main-nav-drawer"
                                        span {
                                            icon("bars")
                                        }
                                    }
                                    strong {
                                        a(href = "/", classes = "brand") {
                                            img(src = "/logo.svg", alt = "", classes = "brand-logo")
                                            +Config.get().instanceName
                                        }
                                    }
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
                        div(classes = "main-nav-backdrop") {
                            attributes["hidden"] = "hidden"
                        }
                        navigation(user, path, subLinks)
                        div(classes = "content") {
                            children()
                        }
                    }
                }
                footer {
                    small { +"Partyboi © 2024-${BuildInfo.buildYear} Jumalauta – built ${BuildInfo.timestamp}" }
                }
                if (messages.isNotEmpty()) {
                    aside(classes = "snackbars") {
                        section(classes = "container") {
                            ul {
                                messages.forEach { message ->
                                    li(classes = message.type.name.lowercase()) {
                                        span { +message.text }
                                        a(href = "#") { +"Dismiss" }
                                    }
                                }
                            }
                        }
                    }
                }
                dialog(classes = "preview-modal") {
                    id = "preview-modal"
                    form {
                        attributes["method"] = "dialog"
                        button(classes = "close") {
                            attributes["aria-label"] = "Close"
                            +"×"
                        }
                    }
                    div { id = "preview-modal-media" }
                }
                script(src = BuildInfo.asset("/assets/partyboi.js")) {}
            }
        }
    }
}

// Icon URLs are plain paths (not BuildInfo.asset) — the logo changes with the theme or an
// asset upload, independent of builds; the routes revalidate with ETags.
fun HEAD.faviconLinks() {
    link(rel = "icon", href = "/logo.svg", type = "image/svg+xml")
    link(rel = "icon", href = "/favicon-32x32.png", type = "image/png") { attributes["sizes"] = "32x32" }
    link(rel = "apple-touch-icon", href = "/apple-touch-icon.png")
    link(rel = "manifest", href = "/site.webmanifest")
}

class Redirection(val location: String) : Renderable {
    override fun getContent(user: User?, path: String): String {
        return ""
    }

    override fun statusCode(): HttpStatusCode {
        return HttpStatusCode.Found
    }

    override fun headers(): Map<String, String> {
        return mapOf("Location" to location)
    }
}
