package party.jml.partyboi.templates.components

import kotlinx.html.*
import party.jml.partyboi.templates.Javascript

fun FlowContent.deleteButton(url: String, tooltipText: String? = null, confirmation: String? = null) {
    button(classes = "flat-button") {
        tooltip(tooltipText)
        onClick = Javascript.build {
            if (confirmation != null) {
                confirm(confirmation) {
                    httpDelete(url)
                    refresh()
                }
            } else {
                httpDelete(url)
                refresh()
            }
        }
        icon("trash-can")
    }
}