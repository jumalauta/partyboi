package party.jml.partyboi.schedule

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader

object SchedulePage {
    fun render(events: List<Event>, timeZone: TimeZone) = Page("Schedule") {
        h1 { +"Schedule" }
        schedule(events, timeZone)
    }
}

fun FlowContent.schedule(events: List<Event>, timeZone: TimeZone) {
    events
        .groupBy { it.startTime.toLocalDateTime(timeZone).date }
        .forEach { (date, events) ->
            article {
                cardHeader("${date.dayOfWeek.name.lowercase().capitalize()} $date")
                table {
                    tbody {
                        events.forEach { event ->
                            tr {
                                td(classes = "narrow") { +event.startTime.toLocalDateTime(timeZone).time.toString() }
                                td { +event.name }
                            }
                        }
                    }
                }
            }
        }
}
