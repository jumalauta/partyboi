package party.jml.partyboi.schedule

import kotlinx.html.*
import party.jml.partyboi.templates.Page

object SchedulePage {
    fun render(events: List<Event>) = Page("Schedule") {
        h1 { +"Schedule" }
        schedule(events)
    }
}

fun FlowContent.schedule(events: List<Event>) {
    events
        .groupBy { it.time.toLocalDate() }
        .forEach { (date, events) ->
            article {
                header { +"${date.dayOfWeek.name} $date" }
                table {
                    tbody {
                        events.forEach { event ->
                            tr {
                                td(classes = "narrow") { +event.time.toLocalTime().toString() }
                                td { +event.name }
                            }
                        }
                    }
                }
            }
        }
}
