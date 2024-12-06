package party.jml.partyboi.schedule

import kotlinx.html.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader

object SchedulePage {
    fun render(events: List<Event>) = Page("Schedule") {
        h1 { +"Schedule" }
        schedule(events)
    }
}

fun FlowContent.schedule(events: List<Event>) {
    events
        .groupBy { it.time.date }
        .forEach { (date, events) ->
            article {
                cardHeader("${date.dayOfWeek.name.lowercase().capitalize()} $date")
                table {
                    tbody {
                        events.forEach { event ->
                            tr {
                                td(classes = "narrow") { +event.time.time.toString() }
                                td { +event.name }
                            }
                        }
                    }
                }
            }
        }
}
