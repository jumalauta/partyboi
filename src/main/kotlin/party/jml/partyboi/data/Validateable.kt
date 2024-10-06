package party.jml.partyboi.data

import arrow.core.*

interface Validateable<T : Validateable<T>> {
    fun validationErrors(): List<Option<ValidationError.Message>>

    fun validate(name: String? = null): Either<ValidationError, T> {
        return validationErrors()
            .flattenOption()
            .toNonEmptyListOrNone().fold(
                {
                    @Suppress("UNCHECKED_CAST")
                    (this as T).right()
                },
                { Either.Left(ValidationError(it)) }
            )
    }

    fun expectNotEmpty(name: String, value: String) =
        cond(name, value, value.isEmpty(), "Value cannot be empty")

    fun expectMinLength(name: String, value: String, minLength: Int) =
        cond(name, value,value.length < minLength, "Minimum length is $minLength characters")

    fun expectMaxLength(name: String, value: String, maxLength: Int) =
        cond(name, value,value.length > maxLength, "Maximum length is $maxLength characters")

    fun expectEqual(name: String, value: String, expected: String) =
        cond(name, value, value != expected, "Value is not equal")

    fun cond(target: String, value: String, errorCondition: Boolean, message: String): Option<ValidationError.Message> =
        if (errorCondition) Some(ValidationError.Message(target, message, value)) else None

}