package party.jml.partyboi.templates

import io.ktor.http.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import party.jml.partyboi.auth.User

data class Page(
    val title: String,
    val children: MAIN.() -> Unit,
) : Renderable {
    override fun getHTML(user: User?): String {
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
                            li { strong { a(href="/") { +"PartyBoi" } } }
                        }
                        ul {
                            if (user == null) {
                                li { a(href = "/login") { +"Login" } }
                                li { a(href = "/register") {
                                    role = "button"
                                    +"Register" }
                                }
                            } else {
                                li { a(href = "/compos") { +"Competitions" } }
                                li { a(href = "/entries") { +"Entries" } }
                                li { a(href = "/vote") { +"Vote" } }
                                if (user.isAdmin) {
                                    li {
                                        details(classes = "dropdown") {
                                            summary { +"Admin" }
                                            ul {
                                                attributes.put("dir", "rtl")
                                                li { a(href = "/admin/compos") { +"Compos" } }
                                                li { a(href = "/admin/screen") { +"Screen" } }
                                            }
                                        }
                                    }
                                }
                                li {
                                    details(classes = "dropdown") {
                                        summary { +user.name }
                                        ul {
                                            attributes.put("dir", "rtl")
                                            li { a(href = "/logout") { +"Log out" } }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    children()

                    footer {
                        small {
                            strong { +"PartyBoi" }
                            +" v.0.1 by Naetti tyttoe/jML"
                        }
                    }
                }
                script(src = "/assets/partyboi.js") {}
            }
        }
    }
}

class RedirectPage(val location: String) : Renderable {
    override fun getHTML(user: User?): String {
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
    override fun getHTML(user: User?): String {
        return ""
    }

    override fun statusCode(): HttpStatusCode {
        return HttpStatusCode.NoContent
    }
}