package party.jml.partyboi.system

import arrow.core.Either
import party.jml.partyboi.data.AppError

typealias AppResult<T> = Either<AppError, T>