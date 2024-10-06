package party.jml.partyboi.templates

import arrow.core.Either
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.data.getAny

interface Renderable {
    fun getHTML(user: User?): String
    fun statusCode(): HttpStatusCode = HttpStatusCode.OK
    fun headers(): Map<String, String> = mapOf()
}

private fun safely(block: () -> Either<AppError, Renderable>) =
    try {
        block()
    } catch (err: Error) {
        Either.Left(InternalServerError(err.message ?: err.toString()))
    }

suspend fun ApplicationCall.respondEither(
    block: () -> Either<AppError, Renderable>,
    vararg retries: (AppError) -> Either<AppError, Renderable>
) {
    var result = safely { block() }
    for (retry in retries) {
        if (result.isRight()) break
        result = safely { retry(result.leftOrNull() ?: throw Error("Unexpected")) }
    }
    respondPage(result.getAny())
}

suspend fun ApplicationCall.respondPage(renderable: Renderable) {
    val user = userSession().getOrNull()
    val text = "<!DOCTYPE html>\n" + renderable.getHTML(user)
    val status = renderable.statusCode()
    renderable.headers().map { (k, v) ->
        response.headers.append(k, v)
    }
    respond(TextContent(text, ContentType.Text.Html.withCharset(Charsets.UTF_8), status))
}
