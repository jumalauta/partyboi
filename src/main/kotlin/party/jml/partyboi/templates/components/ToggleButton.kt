package party.jml.partyboi.templates.components

import kotlinx.html.*
import party.jml.partyboi.templates.Javascript

fun FlowContent.icon(icon: String, tooltipText: String? = null) {
    span {
        tooltip(tooltipText)
        i(classes = Icon(icon).classes) {}
    }
}

fun FlowContent.icon(icon: Icon, tooltipText: String? = null) {
    span {
        tooltip(tooltipText)
        i(classes = icon.classes) {}
    }
}

fun FlowContent.toggleButton(
    toggled: Boolean,
    icons: IconSet,
    urlPrefix: String,
) {
    val shownIcon = icons.get(toggled)
    button(classes = "flat-button ${if (toggled) "on" else "off"}") {
        tooltip(shownIcon.tooltip)
        onClick = Javascript.build {
            httpPut("$urlPrefix/${!toggled}")
            refresh()
        }
        icon(shownIcon)
    }
}

fun FlowContent.labeledToggleButton(
    toggled: Boolean,
    icons: IconSet,
    urlPrefix: String,
    label: String,
    disabled: Boolean = false,
) {
    val shownIcon = icons.get(toggled)
    button(classes = "raised-button") {
        this.disabled = disabled
        onClick = Javascript.build {
            httpPut("$urlPrefix/${!toggled}")
            refresh()
        }
        icon(shownIcon)
        span { +" $label" }
    }
}

data class IconSet(
    val toggled: Icon,
    val notToggled: Icon
) {
    fun get(state: Boolean) = if (state) toggled else notToggled

    companion object {
        val visibility = IconSet(Icon.visible, Icon.hidden)
        val submitting = IconSet(Icon("file-arrow-up", "Submitting open"), Icon("file-arrow-up", "Submitting closed"))
        val voting = IconSet(Icon("check-to-slot", "Voting open"), Icon("check-to-slot", "Voting closed"))
        val resultsPublic = IconSet(
            Icon("square-poll-horizontal", "Results are public"),
            Icon("square-poll-horizontal", "Results are secret")
        )
        val qualified = IconSet(Icon("star", "Qualified"), Icon("star", "Unqualified / disqualified"))
        val scheduled = IconSet(Icon("clock", "Pending"), Icon("ban", "Disabled"))
        val showOnInfoPage =
            IconSet(Icon("circle-info", "Show on info page"), Icon("circle-info", "Do not show on info page"))
        val allowEdit = IconSet(
            Icon("file-arrow-up", "Editing allowed after deadline"),
            Icon("file-arrow-up", "Editing not allowed after deadline")
        )
        val admin = IconSet(Icon.admin, Icon.admin)
    }
}

data class Icon(
    val icon: String,
    val tooltip: String? = null,
) {
    val classes = "fa-solid fa-$icon"

    companion object {
        val visible = Icon("eye", "Visible")
        val hidden = Icon("eye-slash", "Hidden")
        val admin = Icon("brain", "Admin")
    }
}
