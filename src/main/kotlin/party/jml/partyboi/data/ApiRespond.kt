package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import party.jml.partyboi.auth.userSession

suspend fun ApplicationCall.apiRespond(block: () -> Either<AppError, Unit>) {
    Either.catch {
        block().fold(
            { respond(it.statusCode, it.message) },
            { respondText("OK")}
        )
    }.mapLeft {
        respond(HttpStatusCode.InternalServerError, it.message ?: "Fail")
    }
}

fun ApplicationCall.parameterInt(name: String): Either<AppError, Int> =
    try {
        parameters[name]?.toInt()?.right() ?: MissingInput(name).left()
    } catch (_: NumberFormatException) {
        InvalidInput(name).left()
    }

fun ApplicationCall.parameterBoolean(name: String): Either<AppError, Boolean> =
    try {
        parameters[name]?.toBooleanStrict()?.right() ?: MissingInput(name).left()
    } catch (_: IllegalArgumentException) {
        InvalidInput(name).left()
    }

suspend fun ApplicationCall.switchApi(block: (id: Int, state: Boolean) -> Either<AppError, Unit>) {
    apiRespond { either {
        userSession().bind()
        val id = parameterInt("id").bind()
        val state = parameterBoolean("state").bind()
        block(id, state).bind()
    } }
}