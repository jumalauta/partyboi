package party.jml.partyboi.templates

import kotlinx.html.*
import party.jml.partyboi.Config
import party.jml.partyboi.auth.User

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

    val userItems = listOf(
        // NavItem("/schedule", "Schedule"),
        NavItem("/compos", "Compos"),
        NavItem("/entries", "Entries"),
        NavItem("/vote", "Voting"),
        NavItem("/results", "Results"),
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
        li {
            a(href = it.url, classes = if (path != "/" && it.url.startsWith(path)) "active" else "") {
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

fun MAIN.navigation(user: User?, path: String) {
    nav {
        ul {
            li { strong { a(href = "/") { +Config.getInstanceName() } } }
        }
        ul {
            if (user == null) {
                renderItems(path, Navigation.guestItems)
            } else {
                renderItems(path, Navigation.userItems)
                if (user.isAdmin) {
                    navigationDropdown(path, "Admin", Navigation.adminItems)
                }
                navigationDropdown(path, user.name, Navigation.accountItems)
            }
        }
    }
}

