package party.jml.partyboi.templates.components

import io.ktor.util.logging.*
import kotlinx.html.FlowContent
import kotlinx.html.section
import kotlinx.html.span
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.UserError
import party.jml.partyboi.data.randomShortId

fun FlowContent.errorMessage(error: Throwable) {
    section(classes = "error") {
        val id = randomShortId()
        KtorSimpleLogger("renderFields").error("Error {}: {}", id, error)
        +"Something went wrong"
        span {
            tooltip("Error id for debugging: $id")
            +"..."
        }
    }
}

fun FlowContent.errorMessage(error: AppError) {
    section(classes = "error") {
        if (error is UserError) {
            +error.message
        } else {
            val id = randomShortId()
            KtorSimpleLogger("renderFields").error("Error {}: {} {}", id, error.javaClass.simpleName, error.message)
            +"Something went wrong"
            span {
                tooltip("Error id for debugging: $id")
                +"..."
            }
        }
    }
}
