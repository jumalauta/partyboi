package party.jml.partyboi.errors

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.ktor.http.*
import kotlinx.html.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Renderable

interface AppError : Renderable {
    val message: String
    val statusCode: HttpStatusCode

    override fun getHTML(): String {
        val className = this::class.simpleName ?: "AppError"
        val page = Page("Error") {
            article {
                h1 { +className }
                p { +message }
            }
        }
        return page.getHTML()
    }

}

class InternalServerError(override val message: String) : AppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.InternalServerError
}

class DatabaseError(override val message: String) : AppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.InternalServerError
}

class FormError(override val message: String) : AppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.BadRequest
}

class ValidationError(val errors: NonEmptyList<Message>) : AppError {
    constructor(target: String, message: String, value: String) : this(nonEmptyListOf(Message(target, message, value)))
    constructor(message: String, value: String) : this(nonEmptyListOf(Message(null, message, value)))

    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.BadRequest

    override val message: String
        get() = errors.map { if (it.target == null) it.message else "${it.target}: ${it.message}" }.joinToString { it }

    fun valueOf(name: String): String? = errors.find { it.target == name }?.value

    data class Message(val target: String?, val message: String, val value: String)
}

class RedirectInterruption(val location: String) : AppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.Found

    override val message: String
        get() = "Location: $location"

    override fun getHTML(): String =
        Page("Redirecting...") {
            a(href = location) { +"Click here if the browser does not redirect you automatically" }
        }.getHTML()

    override fun headers(): Map<String, String> =
        mapOf("Location" to location)
}