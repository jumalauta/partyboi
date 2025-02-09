package party.jml.partyboi.templates.components

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.section

fun FlowContent.columns(left: (FlowContent.() -> Unit)?, right: (FlowContent.() -> Unit)? = null) {
    if (left != null) {
        if (right != null) {
            section(classes = "two-columns") {
                div(classes = "two-columns-left") { left() }
                div(classes = "two-columns-right") { right() }
            }
        } else {
            div { left() }
        }
    } else if (right != null) {
        div { right() }
    }
}