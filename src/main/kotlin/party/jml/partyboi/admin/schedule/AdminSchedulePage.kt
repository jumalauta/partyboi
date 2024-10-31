package party.jml.partyboi.admin.schedule

import party.jml.partyboi.templates.Page
import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.triggers.FailedTriggerRow
import party.jml.partyboi.triggers.NewScheduledTrigger
import party.jml.partyboi.triggers.TriggerRow
import party.jml.partyboi.triggers.SuccessfulTriggerRow

object AdminSchedulePage {
    fun render(
        events: List<Event>,
        newEvent: Form<NewEvent>,
    ) =
        Page("Schedule") {
            h1 { +"Schedule" }

            columns(
                if (events.isNotEmpty()) {
                    {
                        events
                            .groupBy { it.time.toLocalDate() }
                            .forEach { (date, events) ->
                                article {
                                    header { +"Schedule – ${date.dayOfWeek.name} $date" }
                                    table {
                                        thead {
                                            tr {
                                                th(classes = "narrow") { +"Time" }
                                                th { +"Name" }
                                                th(classes = "narrow") {}
                                            }
                                        }
                                        tbody {
                                            events.forEach { event ->
                                                tr {
                                                    td { +event.time.toLocalTime().toString() }
                                                    td { a(href = "/admin/schedule/events/${event.id}") { +event.name } }
                                                    td(classes = "align-right") {
                                                        deleteButton(
                                                            url = "/admin/schedule/events/${event.id}",
                                                            tooltipText = "Delete event",
                                                            confirmation = "Are you sure you want to delete event '${event.name}'?"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                } else null,
                {
                    article {
                        header { +"New event" }
                        dataForm("/admin/schedule/events") {
                            fieldSet {
                                renderFields(newEvent)
                            }
                            footer { submitInput { value = "Add event" } }
                        }
                    }
                }
            )
        }

    fun renderEdit(
        event: Form<Event>,
        triggers: List<TriggerRow>,
        compos: List<Compo>,
        newTrigger: Form<NewScheduledTrigger>,
    ) =
        Page("Edit event") {
            h1 { +"Edit event" }

            columns({
                article {
                    dataForm("/admin/schedule/events/${event.data.id}") {
                        fieldSet {
                            renderFields(event)
                        }
                        footer { submitInput { value = "Save changes" } }
                    }
                }
            }, {
                if (triggers.isNotEmpty()) {
                    article {
                        header { +"Triggers" }
                        p { +"These actions will happen at the scheduled start time of the event" }
                        table {
                            thead {
                                tr {
                                    th { +"Action" }
                                    th(classes = "narrow") {}
                                }
                            }
                            tbody {
                                triggers.forEach { trigger ->
                                    tr {
                                        td { +trigger.description }
                                        td(classes = "align-right") {
                                            when (trigger) {
                                                is SuccessfulTriggerRow -> icon(
                                                    "circle-check",
                                                    "Triggered at ${trigger.executionTime}"
                                                )

                                                is FailedTriggerRow -> icon("circle-exclamation", trigger.error)
                                                else -> toggleButton(
                                                    trigger.enabled,
                                                    IconSet.scheduled,
                                                    "/admin/schedule/triggers/${trigger.triggerId}/setEnabled"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                article {
                    header { +"Add new trigger" }
                    dataForm("/admin/schedule/triggers") {
                        fieldSet {
                            renderFields(
                                newTrigger, mapOf(
                                    "action" to NewScheduledTrigger.TriggerOptions,
                                    "compoId" to compos
                                )
                            )
                        }
                        footer { submitInput { value = "Add trigger" } }
                    }
                }
            })
        }
}

