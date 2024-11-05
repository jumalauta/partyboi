package party.jml.partyboi.templates

import kotlinx.html.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.auth.User
import party.jml.partyboi.templates.components.icon

data class NavItem(
    val url: String,
    val label: String,
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

fun UL.renderItems(path: String, items: List<NavItem>) {
    items.forEach {
        val isMatch = it.url == path || (it.url != "/" && path.startsWith(it.url))
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
            }
        }
    }
}

fun UL.navigationDropdown(path: String, label: String, items: List<NavItem>) {
    li {
        details(classes = "dropdown") {
            summary { +label }
            ul {
                attributes.put("dir", "rtl")
                renderItems(path, items)
            }
        }
    }
}

fun SECTION.navigation(user: User?, path: String) {
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
                    renderItems(path, Navigation.publicItems)
                    if (user != null) {
                        renderItems(path, Navigation.userItems)
                    }
                }
            }
            if (user?.isAdmin == true) {
                details {
                    attributes["open"] = ""
                    summary { +"Admin" }
                    ul { renderItems(path, Navigation.adminItems) }
                }
            }
        }
    }
}
