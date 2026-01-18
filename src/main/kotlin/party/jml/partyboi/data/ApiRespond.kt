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
import kotlinx.serialization.json.Json
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.form.Form
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.respondAndCatchEither
import party.jml.partyboi.templates.respondPage
import party.jml.partyboi.validation.Validateable
import java.nio.file.Path
import java.util.*

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

suspend inline fun <reified T> ApplicationCall.jsonRespond(noinline block: suspend Raise<AppError>.() -> T) {
    Either.catch {
        val result = either { block() }
        result.fold(
            { respond(it.statusCode, it.message) },
            {
                respondText(Json.encodeToString(it), ContentType.Application.Json)
            }
        )
    }.mapLeft {
        respond(HttpStatusCode.InternalServerError, it.message ?: "Fail")
    }
}

suspend inline fun <reified T : Validateable<T>> ApplicationCall.receiveForm(formFieldLimit: Long): AppResult<Form<T>> =
    Form.fromParameters<T>(receiveMultipart(formFieldLimit = formFieldLimit))

suspend inline fun <reified T : Validateable<T>> ApplicationCall.processForm(
    handleForm: suspend Raise<AppError>.(data: T) -> Renderable,
    crossinline handleError: suspend Raise<AppError>.(formWithErrors: Form<T>) -> Renderable,
    formFieldLimit: Long = -1L,
) {
    receiveForm<T>(formFieldLimit).fold(
        { respondPage(it) },
        { form ->
            val result = form.validated().fold(
                { either { handleError(form.with(it)) } },
                { either { handleForm(it) } }
            )
            respondAndCatchEither(
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

fun ApplicationCall.parameterUUID(name: String): AppResult<UUID> =
    try {
        parameters[name]?.let { UUID.fromString(it) }?.right() ?: MissingInput(name).left()
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

fun ApplicationCall.parameterPathString(name: String) =
    parameters.getAll(name)?.right()?.map {
        it.joinToString("/")
    } ?: MissingInput(name).left()


inline fun <reified T : Enum<T>> ApplicationCall.parameterEnum(name: String) =
    parameters[name]?.let { name ->
        try {
            enumValues<T>().find { it.name.lowercase() == name.lowercase() }?.right()
        } catch (_: IllegalArgumentException) {
            InvalidInput(name).left()
        }
    } ?: MissingInput(name).left()

suspend fun ApplicationCall.switchApiUuid(block: suspend (id: UUID, state: Boolean) -> AppResult<Unit>) {
    apiRespond {
        either {
            userSession(null).bind()
            val id = parameterUUID("id").bind()
            val state = parameterBoolean("state").bind()
            block(id, state).bind()
        }
    }
}

suspend fun ApplicationCall.switchApiString(block: suspend (id: String, state: Boolean) -> AppResult<Unit>) {
    apiRespond {
        either {
            userSession(null).bind()
            val id = parameterString("id").bind()
            val state = parameterBoolean("state").bind()
            block(id, state).bind()
        }
    }
}
