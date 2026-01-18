package party.jml.partyboi.templates.components

import kotlinx.html.A
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.role

fun FlowContent.buttonLink(href: String, block: A.() -> Unit) {
    a(href = href) {
        role = "button"
        block()
    }
}