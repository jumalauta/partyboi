package party.jml.partyboi.templates.components

import kotlinx.html.*

fun FlowContent.reloadSection(block: OUTPUT.() -> Unit) {
    output {
        attributes.put("id", "reload-section")
        block(this)
    }
}