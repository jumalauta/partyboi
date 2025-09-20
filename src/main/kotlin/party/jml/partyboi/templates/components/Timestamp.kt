package party.jml.partyboi.templates.components

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.html.FlowContent
import kotlinx.html.span
import party.jml.partyboi.system.displayDateTime
import party.jml.partyboi.system.displayDuration

fun FlowContent.timestamp(time: Instant, tz: TimeZone) {
    span {
        attributes["title"] = time.displayDateTime(tz)
        +time.displayDuration()
        +" ago"
    }
}