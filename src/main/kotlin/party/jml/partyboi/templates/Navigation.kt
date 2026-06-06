package party.jml.partyboi.templates

import kotlinx.html.*
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

    fun userItems(user: User) = listOf(
        NavItem("/entries", "Entries"),
        if (user.votingEnabled) {
            NavItem("/vote", "Voting")
        } else {
            NavItem("/vote/register", "Register vote key")
        },
    )

    val adminGroups: List<Pair<String, List<NavItem>>> = listOf(
        "Admin" to listOf(
            NavItem("/admin/settings", "Settings"),
            NavItem("/admin/voting", "Vote keys"),
            NavItem("/admin/users", "Users"),
            NavItem("/admin/compos", "Compos"),
            NavItem("/admin/schedule", "Schedule"),
            NavItem("/admin/screen", "Info screen"),
            NavItem("/admin/assets", "Assets"),
            NavItem("/sync", "Synchronization"),
        ),
        "Debug" to listOf(
            NavItem("/admin/tasks", "Tasks"),
            NavItem("/admin/errors", "Error log"),
        ),
    )

    val accountItems = listOf(
        NavItem("/profile", "Profile"),
        NavItem("/logout", "Logout")
    )
}

fun UL.renderItems(path: String, items: List<NavItem>, subLinks: List<NavItem>) {
    items.forEach {
        val isExactMatch = it.url == path
        val isPartialMatch = it.url != "/" && path.startsWith(it.url)
        li {
            a(
                href = it.url,
                classes = "secondary"
            ) {
                if (isExactMatch || (isPartialMatch && subLinks.isEmpty())) {
                    attributes["aria-current"] = "page"
                }
                if (it.button) {
                    role = "button"
                }
                +it.label
            }
            if (isPartialMatch && subLinks.isNotEmpty()) {
                ul {
                    renderItems(path, subLinks, emptyList())
                }
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
                renderItems(path, items, emptyList())
            }
        }
    }
}

fun SECTION.navigation(user: User?, path: String, subLinks: List<NavItem>) {
    aside(classes = "main-nav") {
        id = "main-nav-drawer"
        attributes["aria-label"] = "Site navigation"

        button(classes = "mobile-nav-close mobile-only flat-button") {
            attributes["aria-label"] = "Close navigation"
            span {
                icon("xmark")
            }
        }

        nav(classes = "page-nav") {
            details {
                attributes["open"] = ""
                summary { +"Navigation" }
                ul {
                    renderItems(path, Navigation.publicItems, subLinks)
                    if (user != null) {
                        renderItems(path, Navigation.userItems(user), subLinks)
                    }
                }
            }
            if (user?.isAdmin == true) {
                Navigation.adminGroups.forEach { (label, items) ->
                    details {
                        attributes["open"] = ""
                        summary { +label }
                        ul { renderItems(path, items, subLinks) }
                    }
                }
            }
        }
    }
}
