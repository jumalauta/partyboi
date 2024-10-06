package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.ktor.http.*
import kotlinx.html.article
import kotlinx.html.h1
import kotlinx.html.p
import party.jml.partyboi.auth.User
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Renderable

interface AppError : Renderable {
    val message: String
    val statusCode: HttpStatusCode

    override fun getHTML(user: User?): String {
        val className = this::class.simpleName ?: "AppError"
        val page = Page("Error") {
            article {
                h1 { +className }
                p { +message }
            }
        }
        return page.getHTML(user)
    }

}

interface SoftAppError : AppError

class InternalServerError(override val message: String) : AppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.InternalServerError
}

class DatabaseError(override val message: String) : AppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.InternalServerError
}

class FormError(override val message: String) : SoftAppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.BadRequest
}

class ValidationError(val errors: NonEmptyList<Message>) : SoftAppError {
    constructor(target: String, message: String, value: String) : this(nonEmptyListOf(Message(target, message, value)))
    constructor(message: String, value: String) : this(nonEmptyListOf(Message(null, message, value)))

    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.BadRequest

    override val message: String
        get() = errors.map { if (it.target == null) it.message else "${it.target}: ${it.message}" }.joinToString { it }

    data class Message(val target: String?, val message: String, val value: String)
}

class RedirectInterruption(val location: String) : AppError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.Found

    override val message: String
        get() = "Location: $location"

    override fun getHTML(user: User?): String = ""

    override fun headers(): Map<String, String> =
        mapOf("Location" to location)
}

class Unauthorized : AppError {
    override val statusCode: HttpStatusCode = HttpStatusCode.Unauthorized
    override val message: String = "Unauthorized"
}

class NotFound : AppError {
    override val statusCode: HttpStatusCode = HttpStatusCode.NotFound
    override val message: String = "Not found"
}

class InvalidInput(name: String) : AppError {
    override val statusCode: HttpStatusCode = HttpStatusCode.BadRequest
    override val message: String = "Invalid input: $name"
}

class MissingInput(name: String) : AppError {
    override val statusCode: HttpStatusCode = HttpStatusCode.BadRequest
    override val message: String = "Missing input: $name"
}

class Forbidden : AppError {
    override val statusCode: HttpStatusCode = HttpStatusCode.Forbidden
    override val message: String = "Forbidden"
}

fun <A> catchError(f: () -> A): Either<AppError, A> =
    Either.catch { f() }.mapLeft { InternalServerError(it.message ?: it.toString()) }
