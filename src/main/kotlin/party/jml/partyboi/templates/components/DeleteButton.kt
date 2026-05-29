package party.jml.partyboi.templates.components

import kotlinx.html.*
import party.jml.partyboi.templates.Javascript

fun FlowContent.deleteButton(
    url: String,
    tooltipText: String? = null,
    confirmation: String? = null,
    label: String? = null,
    redirectUrl: String? = null,
    classes: String = "flat-button",
) {
    button(classes = classes) {
        tooltip(tooltipText)
        onClick = Javascript.build {
            val onDelete: Javascript.() -> Unit = {
                httpDelete(url)
                if (redirectUrl != null) goto(redirectUrl) else refresh()
            }
            if (confirmation != null) confirm(confirmation, onDelete) else onDelete()
        }
        icon("trash-can")
        if (label != null) +" $label"
    }
}