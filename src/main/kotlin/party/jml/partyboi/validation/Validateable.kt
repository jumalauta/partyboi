package party.jml.partyboi.validation

import arrow.core.*
import party.jml.partyboi.data.ValidationError
import kotlin.reflect.KClass

interface Validateable<T : Validateable<T>> {
    fun validationErrors(): List<Option<ValidationError.Message>> = emptyList()
    fun suggestedValues(): Map<String, String> = emptyMap()

    fun validate(kclass: KClass<T>): Either<ValidationError, T> {
        val errors = ValidationAnnotations.validate(kclass, this) + validationErrors().flattenOption()
        return errors.toNonEmptyListOrNone().fold(
            {
                @Suppress("UNCHECKED_CAST")
                (this as T).right()
            },
            { Either.Left(ValidationError(it)) }
        )
    }

    fun expectEqual(name: String, value: String, expected: String) =
        cond(name, value, value != expected, "Value is not equal")

    fun cond(target: String, value: String, errorCondition: Boolean, message: String): Option<ValidationError.Message> =
        if (errorCondition) Some(ValidationError.Message(target, message, value)) else None
}

