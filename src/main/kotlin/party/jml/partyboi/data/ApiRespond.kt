package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.form.Form
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage
import party.jml.partyboi.validation.Validateable
import java.nio.file.Path

suspend fun ApplicationCall.apiRespond(block: suspend Raise<AppError>.() -> Unit) {
    Either.catch {
        val result = either { block() }
        apiRespond(result)
    }.mapLeft {
        respond(HttpStatusCode.InternalServerError, it.message ?: "Fail")
    }
}

suspend fun ApplicationCall.apiRespond(result: AppResult<Unit>) {
    result.fold(
        { respond(it.statusCode, it.message) },
        { respondText("OK") }
    )
}


suspend inline fun <reified T : Validateable<T>> ApplicationCall.receiveForm() =
    Form.fromParameters<T>(receiveMultipart())

suspend inline fun <reified T : Validateable<T>> ApplicationCall.processForm(
    handleForm: suspend Raise<AppError>.(data: T) -> Renderable,
    crossinline handleError: suspend Raise<AppError>.(formWithErrors: Form<T>) -> Renderable
) {
    receiveForm<T>().fold(
        { respondPage(it) },
        { form ->
            val result = form.validated().fold(
                { either { handleError(form.with(it)) } },
                { either { handleForm(it) } }
            )
            respondEither(
                { result.bind() },
                { handleError(form.with(it)) }
            )
        }
    )
}

fun ApplicationCall.parameterString(name: String): AppResult<String> =
    parameters[name]?.right() ?: MissingInput(name).left()

fun ApplicationCall.parameterInt(name: String): AppResult<Int> =
    try {
        parameters[name]?.toInt()?.right() ?: MissingInput(name).left()
    } catch (_: NumberFormatException) {
        InvalidInput(name).left()
    }

fun ApplicationCall.parameterBoolean(name: String): AppResult<Boolean> =
    try {
        parameters[name]?.toBooleanStrict()?.right() ?: MissingInput(name).left()
    } catch (_: IllegalArgumentException) {
        InvalidInput(name).left()
    }

fun ApplicationCall.parameterPath(name: String, nameToPath: (String) -> Path) =
    parameters.getAll(name)?.right()?.map {
        nameToPath(it.joinToString("/"))
    } ?: MissingInput(name).left()

suspend fun ApplicationCall.switchApi(block: suspend (id: Int, state: Boolean) -> AppResult<Unit>) {
    apiRespond {
        either {
            userSession(null).bind()
            val id = parameterInt("id").bind()
            val state = parameterBoolean("state").bind()
            block(id, state).bind()
        }
    }
}
