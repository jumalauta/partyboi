package party.jml.partyboi.data

import arrow.core.*
import io.ktor.http.*
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.FormError
import party.jml.partyboi.errors.ValidationError

class FormReader(private val parameters: Parameters) {
    fun string(key: String): Either<AppError, String> {
        return parameters[key]
            .toOption()
            .toEither { FormError("Value $key is missing") }
    }

    fun int(key: String): Either<AppError, Int> {
        return parameters[key]
            .toOption()
            .toEither { FormError("Value $key is missing") }
            .flatMap { str ->
                try {
                    str.toInt().right()
                } catch (err: NumberFormatException) {
                    ValidationError(key, "Not a number", str).left()
                }
            }
    }
}