package party.jml.partyboi.templates.components

import kotlinx.html.FlowContent

fun FlowContent.tooltip(text: String?) {
    if (text != null) {
        attributes.put("data-tooltip", text)
    }
}