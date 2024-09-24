package party.jml.partyboi.templates

import arrow.core.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.InternalServerError
import party.jml.partyboi.errors.ValidationError

interface Renderable {
    fun getHTML(): String
    fun statusCode(): HttpStatusCode = HttpStatusCode.OK
    fun headers(): Map<String, String> = mapOf()
}

suspend fun ApplicationCall.respondEither(errorHandler: ((ValidationError) -> Either<AppError, Renderable>)? = null, block: () -> Either<AppError, Renderable>) {
    val result = try {
        block()
    } catch (err: Error) {
        Either.Left(InternalServerError(err.message ?: err.toString()))
    }
    result.fold({ error ->
        if (error is ValidationError) {
            errorHandler
                .toOption()
                .toEither { error }
                .flatMap { handler -> handler(error) }
                .fold({ respondPage(it) }, { respondPage(it) })
        } else {
            respondPage(error)
        }
    }, {
        respondPage(it)
    })
}

suspend fun ApplicationCall.respondPage(renderable: Renderable) {
    val text = "<!DOCTYPE html>\n" + renderable.getHTML()
    val status = renderable.statusCode()
    renderable.headers().map { (k, v) ->
        response.headers.append(k, v)
    }
    respond(TextContent(text, ContentType.Text.Html.withCharset(Charsets.UTF_8), status))
}
