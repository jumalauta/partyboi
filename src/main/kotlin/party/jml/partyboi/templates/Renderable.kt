package party.jml.partyboi.templates

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.data.getAny
import party.jml.partyboi.messages.Message
import party.jml.partyboi.services
import party.jml.partyboi.system.AppResult

interface Renderable {
    fun getHTML(user: User?, path: String): String
    fun statusCode(): HttpStatusCode = HttpStatusCode.OK
    fun headers(): Map<String, String> = mapOf()
}

interface Themeable {
    fun setTheme(theme: Theme)
}

interface Messaging {
    fun setMessages(messages: List<Message>)
}


private suspend fun safely(block: suspend () -> AppResult<Renderable>) =
    try {
        block()
    } catch (err: Error) {
        Either.Left(InternalServerError(err))
    }

suspend fun ApplicationCall.respondEither(
    block: suspend Raise<AppError>.() -> Renderable,
) {
    val result = safely { either { block() } }
    respondPage(result.getAny())
}

suspend fun ApplicationCall.respondAndCatchEither(
    block: suspend Raise<AppError>.() -> Renderable,
    vararg retries: suspend Raise<AppError>.(AppError) -> Renderable
) {
    var result = safely {
        either { block() }
    }
    for (retry in retries) {
        if (result.isRight()) break
        result = safely {
            either {
                retry(result.leftOrNull() ?: throw Error("Unexpected"))
            }
        }
    }
    respondPage(result.getAny())
}

suspend fun ApplicationCall.respondPage(renderable: Renderable) {
    val user = userSession(null).getOrNull()

    if (renderable is Themeable) {
        either {
            val theme = application.services().settings.getTheme().bind()
            renderable.setTheme(theme)
        }
    }

    if (renderable is Messaging) {
        user?.id?.let { userId ->
            either {
                val messages = application.services().messages.consumeUnreadMessages(userId).bind()
                renderable.setMessages(messages)
            }
        }
    }

    val text = "<!DOCTYPE html>\n" + renderable.getHTML(user, request.path())
    val status = renderable.statusCode()
    renderable.headers().map { (k, v) ->
        response.headers.append(k, v)
    }
    respond(TextContent(text, ContentType.Text.Html.withCharset(Charsets.UTF_8), status))
}
