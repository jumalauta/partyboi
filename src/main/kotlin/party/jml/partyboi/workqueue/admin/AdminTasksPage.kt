package party.jml.partyboi.workqueue.admin

import kotlinx.html.*
import kotlinx.serialization.json.Json
import party.jml.partyboi.system.admin.renderJson
import party.jml.partyboi.templates.Page
import party.jml.partyboi.workqueue.Task
import party.jml.partyboi.workqueue.TaskRow

object AdminTasksPage {
    fun renderList(tasks: List<TaskRow>): Page = Page("Tasks") {
        h1 { +"Tasks" }
        article {
            table(classes = "compact striped") {
                thead {
                    tr {
                        th { +"Created" }
                        th { +"Type" }
                        th { +"State" }
                        th { +"Finished" }
                        th { +"Details" }
                    }
                }
                tbody {
                    tasks.forEach { task ->
                        tr {
                            td { +task.createdAt.toString() }
                            td { +(task.task::class.simpleName ?: "Unknown") }
                            td { +task.state.name }
                            td { +(task.finishedAt?.toString() ?: "") }
                            td {
                                a(href = "/admin/tasks/${task.id}") { +"Show" }
                            }
                        }
                    }
                }
            }
        }
    }

    fun renderDetails(task: TaskRow): Page = Page("Task ${task.id}") {
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
                    td { +task.createdAt.toString() }
                }
                tr {
                    th { +"Finished" }
                    td { +(task.finishedAt?.toString() ?: "") }
                }
                tr {
                    th { +"Payload" }
                    td { renderJson(Json.encodeToString(Task.serializer(), task.task)) }
                }
            }
        }
    }
}
