package party.jml.partyboi.workqueue.admin

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.html.*
import kotlinx.serialization.json.Json
import party.jml.partyboi.system.admin.renderJson
import party.jml.partyboi.system.displayDateTime
import party.jml.partyboi.templates.Page
import party.jml.partyboi.workqueue.Task
import party.jml.partyboi.workqueue.TaskRow

object AdminTasksPage {
    fun renderList(tasks: List<TaskRow>, tz: TimeZone): Page = Page("Tasks") {
        h1 { +"Tasks" }
        article {
            table(classes = "compact striped") {
                thead {
                    tr {
                        th { +"Created" }
                        th { +"Started" }
                        th { +"Finished" }
                        th { +"Type" }
                        th { +"State" }
                        th { +"Details" }
                    }
                }
                tbody {
                    tasks.forEach { task ->
                        tr {
                            td { +task.createdAt.displayDateTime(tz) }
                            td { +formatNullableTime(task.startedAt, tz) }
                            td { +formatNullableTime(task.finishedAt, tz) }
                            td { +(task.task::class.simpleName ?: "Unknown") }
                            td { +task.state.name }
                            td {
                                a(href = "/admin/tasks/${task.id}") { +"Show" }
                            }
                        }
                    }
                }
            }
        }
    }

    fun renderDetails(task: TaskRow, tz: TimeZone): Page = Page("Task ${task.id}") {
        h1 { +"Task" }

        table(classes = "compact striped") {
            tbody {
                tr {
                    th { +"Id" }
                    td { +task.id.toString() }
                }
                tr {
                    th { +"Type" }
                    td { +(task.task::class.simpleName ?: "Unknown") }
                }
                tr {
                    th { +"State" }
                    td { +task.state.name }
                }
                tr {
                    th { +"Created" }
                    td { +task.createdAt.displayDateTime(tz) }
                }
                tr {
                    th { +"Started" }
                    td { +formatNullableTime(task.startedAt, tz) }
                }
                tr {
                    th { +"Finished" }
                    td { +formatNullableTime(task.finishedAt, tz) }
                }
                tr {
                    th { +"Payload" }
                    td { renderJson(Json.encodeToString(Task.serializer(), task.task)) }
                }
            }
        }
    }

    private fun formatNullableTime(time: Instant?, tz: TimeZone): String =
        time?.displayDateTime(tz) ?: ""
}
