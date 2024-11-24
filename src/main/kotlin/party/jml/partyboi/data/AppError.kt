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
    val throwable: Throwable?

    override fun getHTML(user: User?, path: String): String {
        val className = this::class.simpleName ?: "AppError"
        val page = Page("Error") {
            article {
                h1 { +className }
                p { +message }
            }
        }
        return page.getHTML(user, path)
    }

}

interface UserError : AppError {
    override val throwable: Throwable?
        get() = null
}

class Notice(override val message: String) : UserError {
    override val statusCode: HttpStatusCode = HttpStatusCode.BadRequest
}

class InternalServerError(override val throwable: Throwable) : AppError {
    override val message: String = throwable.message ?: throwable.toString()
    override val statusCode: HttpStatusCode = HttpStatusCode.InternalServerError
}

class DatabaseError(override val throwable: Throwable) : AppError {
    override val message: String = throwable.message ?: throwable.toString()
    override val statusCode: HttpStatusCode = HttpStatusCode.InternalServerError
}

class FormError(override val message: String) : UserError {
    override val statusCode: HttpStatusCode = HttpStatusCode.BadRequest
}

class ValidationError(val errors: NonEmptyList<Message>) : UserError {
    constructor(target: String, message: String, value: String) : this(nonEmptyListOf(Message(target, message, value)))
    constructor(message: String, value: String) : this(nonEmptyListOf(Message(null, message, value)))

    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.BadRequest

    override val message: String
        get() = errors.map { if (it.target == null) it.message else "${it.target}: ${it.message}" }.joinToString { it }

    data class Message(val target: String?, val message: String, val value: String)
}

class RedirectInterruption(val location: String) : UserError {
    override val statusCode: HttpStatusCode
        get() = HttpStatusCode.Found

    override val message: String
        get() = "Location: $location"

    override fun getHTML(user: User?, path: String): String = ""

    override fun headers(): Map<String, String> =
        mapOf("Location" to location)
}

class Unauthorized : UserError {
    override val statusCode: HttpStatusCode = HttpStatusCode.Unauthorized
    override val message: String = "Unauthorized"
}

class NotFound(override val message: String) : UserError {
    override val statusCode: HttpStatusCode = HttpStatusCode.NotFound
}

class InvalidInput(name: String) : UserError {
    override val statusCode: HttpStatusCode = HttpStatusCode.BadRequest
    override val message: String = "Invalid input: $name"
}

class MissingInput(name: String) : UserError {
    override val statusCode: HttpStatusCode = HttpStatusCode.BadRequest
    override val message: String = "Missing input: $name"
}

class Forbidden : UserError {
    override val statusCode: HttpStatusCode = HttpStatusCode.Forbidden
    override val message: String = "Forbidden"
}

class NotReady : AppError {
    override val statusCode: HttpStatusCode = HttpStatusCode.InternalServerError
    override val message: String = "Not ready"
    override val throwable: Throwable? = null
}

fun <A> catchError(f: () -> A): Either<AppError, A> =
    Either.catch { f() }.mapLeft {
        InternalServerError(it)
    }
