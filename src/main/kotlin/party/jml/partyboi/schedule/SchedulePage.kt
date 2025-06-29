package party.jml.partyboi.schedule

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.system.displayDate
import party.jml.partyboi.system.toDate
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
        .groupBy { it.startTime.toDate() }
        .forEach { (date, events) ->
            article {
                cardHeader(date.displayDate())
                table {
                    tbody {
                        events.forEach { event ->
                            tr {
                                td(classes = "narrow") { +event.formatTime(timeZone) }
                                td { +event.name }
                            }
                        }
                    }
                }
            }
        }
    article {
        a(href = "/schedule.ics") {
            +"Download calendar"
        }
    }
}
