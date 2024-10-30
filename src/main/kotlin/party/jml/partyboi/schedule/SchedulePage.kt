package party.jml.partyboi.schedule

import party.jml.partyboi.templates.Page
import kotlinx.html.*
import party.jml.partyboi.templates.components.deleteButton

object SchedulePage {
    fun render(events: List<Event>) = Page("Schedule") {
        h1 { +"Schedule" }
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
}