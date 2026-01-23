package party.jml.partyboi.schedule

import kotlinx.html.*
import party.jml.partyboi.system.TimeService
import party.jml.partyboi.system.displayDate
import party.jml.partyboi.system.toDate
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader

object SchedulePage {
    fun render(events: List<Event>) = Page("Schedule") {
        h1 { +"Schedule" }
        schedule(events)
    }
}

fun FlowContent.schedule(events: List<Event>) {
    val eventsByDate = events.groupBy { it.startTime.toDate() }
    val timeZones = eventsByDate.keys.associateWith { TimeService.timeZoneAt(it) }
    val showTimeZones = timeZones.values.distinct().count() > 1

    eventsByDate
        .forEach { (date, events) ->
            val timeZone = timeZones[date]!!
            article {
                cardHeader(
                    if (showTimeZones) {
                        "${date.displayDate()} (${timeZone})"
                    } else {
                        date.displayDate()
                    }
                )
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
