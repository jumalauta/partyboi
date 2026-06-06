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

/**
 * Standard confirmation prompt for destructive delete actions, e.g.
 * `Delete event "Summer Compo"?`. Pass [cascade] for actions that also remove
 * children, e.g. `confirmDelete("slideset", name, cascade = "all its slides")`
 * → `Delete slideset "Foo" and all its slides?`.
 */
fun confirmDelete(thing: String, name: String? = null, cascade: String? = null): String {
    val subject = if (name != null) "$thing \"$name\"" else thing
    val tail = if (cascade != null) " and $cascade" else ""
    return "Delete $subject$tail?"
}