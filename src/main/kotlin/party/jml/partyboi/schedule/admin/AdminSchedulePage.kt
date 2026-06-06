package party.jml.partyboi.schedule.admin

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.system.displayDate
import party.jml.partyboi.system.toDate
import party.jml.partyboi.system.toLocalTimeString
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.triggers.FailedTriggerRow
import party.jml.partyboi.triggers.NewScheduledTrigger
import party.jml.partyboi.triggers.SuccessfulTriggerRow
import party.jml.partyboi.triggers.TriggerRow

object AdminSchedulePage {
    fun render(
        newEventForm: Form<NewEvent>,
        events: List<Event>,
        timeZone: TimeZone
    ) =
        Page("Schedule") {
            h1 { +"Schedule" }

            reloadSection {
                if (events.isNotEmpty()) {
                    label(classes = "shift-step") {
                        +"Running late? Shift step (minutes): "
                        numberInput {
                            id = "shift-step"
                            value = "15"
                            attributes["min"] = "1"
                        }
                    }
                    events
                        .groupBy { it.startTime.toDate() }
                        .forEach { (date, events) ->
                            article {
                                header { +date.displayDate() }
                                table(classes = "schedule") {
                                    thead {
                                        tr {
                                            th { +"Time" }
                                            th { +"Name" }
                                            th(classes = "settings") {}
                                        }
                                    }
                                    tbody {
                                        events.forEach { event -> eventRow(event) }
                                    }
                                }
                            }
                        }
                }
                renderForm(
                    title = "New event",
                    url = "/admin/schedule/events",
                    form = newEventForm,
                    submitButtonLabel = "Add event",
                    ajax = true,
                )
            }
            flatpickr()
        }

    // A single editable schedule row: in-place time/name inputs that save on change,
    // and one compact column with all controls (nudge, visibility, shift, triggers, delete).
    private fun TBODY.eventRow(event: Event) {
        val id = event.id
        tr {
            td(classes = "event-time") {
                textInput {
                    type = InputType.dateTime
                    value = event.startTime.toLocalTimeString()
                    attributes["data-save-url"] = "/admin/schedule/events/$id/startTime"
                    attributes["data-time-only"] = "true"
                    attributes["aria-label"] = "Start time"
                }
                textInput {
                    type = InputType.dateTime
                    value = event.endTime?.toLocalTimeString() ?: ""
                    attributes["data-save-url"] = "/admin/schedule/events/$id/endTime"
                    attributes["data-time-only"] = "true"
                    attributes["aria-label"] = "End time"
                }
            }
            td(classes = "event-name wide") {
                textInput {
                    value = event.name
                    attributes["data-save-url"] = "/admin/schedule/events/$id/name"
                    attributes["aria-label"] = "Event name"
                }
            }
            td(classes = "settings") {
                button(classes = "flat-button") {
                    tooltip("15 minutes earlier")
                    onClick = Javascript.build {
                        httpPut("/admin/schedule/events/$id/nudge/-15")
                        refresh()
                    }
                    icon("minus")
                }
                button(classes = "flat-button") {
                    tooltip("15 minutes later")
                    onClick = Javascript.build {
                        httpPut("/admin/schedule/events/$id/nudge/15")
                        refresh()
                    }
                    icon("plus")
                }
                toggleButton(
                    event.visible,
                    IconSet.visibility,
                    "/admin/schedule/events/$id/setVisible",
                )
                button(classes = "flat-button") {
                    tooltip("Shift this and all later events by the step above")
                    onClick = "shiftRest('$id')"
                    icon("forward")
                }
                button(classes = "flat-button") {
                    tooltip("Triggers")
                    onClick = Javascript.build { goto("/admin/schedule/events/$id") }
                    icon("bolt")
                }
                deleteButton(
                    url = "/admin/schedule/events/$id",
                    tooltipText = "Delete event",
                    confirmation = confirmDelete("event", event.name)
                )
            }
        }
    }

    fun renderEdit(
        eventForm: Form<Event>,
        newTriggerForm: Form<NewScheduledTrigger>,
        triggers: List<TriggerRow>,
        compos: List<Compo>,
    ) =
        Page("Edit event") {
            h1 { +"Edit event" }

            columns({
                renderForm(
                    url = "/admin/schedule/events/${eventForm.data.id}",
                    form = eventForm,
                )
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
                                        td(classes = "settings") {
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

                renderForm(
                    title = "New trigger",
                    url = "/admin/schedule/triggers",
                    form = newTriggerForm,
                    options = mapOf(
                        "action" to NewScheduledTrigger.TriggerOptions,
                        "compoId" to compos
                    ),
                    submitButtonLabel = "Add trigger"
                )
            })
            flatpickr()
        }

    fun FlowContent.flatpickr() {
        link {
            rel = "stylesheet"
            href = "/assets/flatpickr.min.css"
        }
        script(src = "/assets/flatpickr.js") {}
    }
}

