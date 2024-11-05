package party.jml.partyboi.templates

import kotlinx.html.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.auth.User
import party.jml.partyboi.templates.components.icon

data class NavItem(
    val url: String,
    val label: String,
    val getSubLinks: (app: AppServices) -> List<NavItem> = { emptyList() },
    val button: Boolean = false,
)

object Navigation {
    val guestItems = listOf(
        NavItem("/login", "Login"),
        NavItem("/register", "Register", button = true),
    )

    val publicItems = listOf(
        NavItem("/", "Info"),
        NavItem("/compos", "Compos"),
        NavItem("/results", "Results"),
    )

    val userItems = listOf(
        NavItem("/entries", "Entries"),
        NavItem("/vote", "Voting"),
    )

    val adminItems = listOf(
        NavItem("/admin/compos", "Compos"),
        NavItem("/admin/schedule", "Schedule"),
        NavItem("/admin/screen", "Info screen"),
        NavItem("/admin/assets", "Assets"),
    )

    val accountItems = listOf(
        NavItem("/logout", "Log out")
    )
}

fun UL.renderItems(app: AppServices, path: String, items: List<NavItem>) {
    items.forEach {
        val isMatch = it.url == path || (path != "/" && path.startsWith(it.url))
        li {
            a(
                href = it.url,
                classes = "secondary"
            ) {
                if (isMatch) {
                    attributes["aria-current"] = "page"
                }
                if (it.button) {
                    role = "button"
                }
                +it.label

                val subLinks = it.getSubLinks(app)
                if (subLinks.isNotEmpty()) {
                    ul {
                        renderItems(app, path, subLinks)
                    }
                }
            }
        }
    }
}

fun UL.navigationDropdown(app: AppServices, path: String, label: String, items: List<NavItem>) {
    li {
        details(classes = "dropdown") {
            summary { +label }
            ul {
                attributes.put("dir", "rtl")
                renderItems(app, path, items)
            }
        }
    }
}

fun SECTION.navigation(app: AppServices, user: User?, path: String) {
    aside(classes = "main-nav") {
        header(classes = "mobile-only") {
            nav {
                ul {
                    li {
                        strong { a(href = "/") { +Config.getInstanceName() } }
                    }
                }
                ul {
                    li {
                        button(classes = "mobile-nav-button flat-button") {
                            span {
                                icon("xmark")
                            }
                        }
                    }
                }
            }
        }

        nav(classes = "page-nav") {
            details {
                attributes["open"] = ""
                summary { +"Navigation" }
                ul {
                    renderItems(app, path, Navigation.publicItems)
                    if (user != null) {
                        renderItems(app, path, Navigation.userItems)
                    }
                }
            }
            if (user?.isAdmin == true) {
                details {
                    attributes["open"] = ""
                    summary { +"Admin" }
                    ul { renderItems(app, path, Navigation.adminItems) }
                }
            }
        }
    }
}
