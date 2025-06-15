package party.jml.partyboi.system.admin

import kotlinx.html.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import party.jml.partyboi.system.ErrorRow
import party.jml.partyboi.templates.Page

object AdminErrorLogPage {
    fun renderList(errors: List<ErrorRow>): Page = Page("Error log") {
        h1 { +"Error log" }
        article {
            table(classes = "compact striped") {
                thead {
                    tr {
                        th { +"Key" }
                        th { +"Time" }
                        th { +"Message" }
                        th { +"Stack trace" }
                    }
                }
                tbody {
                    errors.forEach { error ->
                        tr {
                            td { +error.key }
                            td { +error.time.toString() }
                            td { +error.message }
                            td {
                                a(href = "/admin/errors/${error.id}") {
                                    +"Show"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun renderStackTrace(error: ErrorRow): Page = Page("Error ${error.key}") {
        h1 { +"Stack trace" }

        table(classes = "compact striped") {
            tbody {
                tr {
                    th { +"Error key" }
                    td { +error.key }
                }
                tr {
                    th { +"Message" }
                    td { +error.message }
                }
                error.trace?.let { trace ->
                    tr {
                        th { +"Stack trace" }
                        td { +trace }
                    }
                }
                error.context?.let { ctx ->
                    tr {
                        th { +"Context" }
                        td { renderJson(ctx) }
                    }
                }
            }
        }
    }
}

fun FlowContent.renderJson(json: String) {
    val element = Json.parseToJsonElement(json)
    pre {
        +prettyJsonFormat.encodeToString(JsonElement.serializer(), element)
    }
}

@OptIn(ExperimentalSerializationApi::class)
val prettyJsonFormat = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}