package party.jml.partyboi.templates.components

import kotlinx.html.*

/**
 * A single tab: a label and the route it links to. Each tab is its own page/route;
 * [active] marks the one currently being viewed.
 */
data class Tab(
    val label: String,
    val href: String,
    val active: Boolean = false,
)

/**
 * A row of tab links. Tabs navigate between separate routes (no client-side
 * switching), so the caller renders only the active tab's content below the bar.
 */
fun FlowContent.tabBar(items: List<Tab>) {
    div(classes = "tab-bar") {
        items.forEach { tab ->
            a(href = tab.href, classes = "tab" + if (tab.active) " active" else "") {
                if (tab.active) attributes["aria-current"] = "page"
                +tab.label
            }
        }
    }
}
