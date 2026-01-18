package party.jml.partyboi.system

import arrow.core.Either
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InternalServerError

typealias AppResult<T> = Either<AppError, T>

inline fun <T> catchResult(f: () -> T): AppResult<T> =
    Either.catch(f).mapLeft { InternalServerError(it) }

fun AppResult<Boolean>.isTrue(): Boolean = fold({ false }, { it })