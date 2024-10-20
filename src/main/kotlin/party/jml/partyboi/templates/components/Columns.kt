package party.jml.partyboi.templates.components

import kotlinx.html.*

fun FlowContent.columns(vararg blocks: DIV.() -> Unit) {
    section(classes = "columns") {
        blocks.forEach { block ->
            div { block() }
        }
    }
}