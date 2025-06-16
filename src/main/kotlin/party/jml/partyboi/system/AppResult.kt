package party.jml.partyboi.system

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.fold
import party.jml.partyboi.data.AppError

typealias AppResult<T> = Either<AppError, T>

suspend inline fun <Error, A> suspendEither(block: suspend Raise<Error>.() -> A): Either<Error, A> =
    fold(
        block = { block.invoke(this) },
        recover = { Either.Left(it) },
        transform = { Either.Right(it) }
    )