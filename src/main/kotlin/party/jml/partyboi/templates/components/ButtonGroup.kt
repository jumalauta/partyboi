package party.jml.partyboi.templates.components

import kotlinx.html.*

fun FlowContent.buttonGroup(block: DIV.() -> Unit) {
    div {
        attributes["role"] = "group"
        block()
    }
}