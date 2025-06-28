package party.jml.partyboi.validation

import arrow.core.*
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.data.isValidEmailAddress
import party.jml.partyboi.form.FileUpload
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object ValidationAnnotations {
    fun validate(kclass: KClass<*>, data: Any): List<ValidationError.Message> {
        println("Validate $kclass: ${kclass.primaryConstructor}")
        return kclass.primaryConstructor!!.parameters.flatMap { param ->
            param.annotations.toNonEmptyListOrNone().map { annotations ->
                kclass.memberProperties
                    .find { it.name == param.name }
                    ?.let { prop ->
                        validate(
                            name = param.name!!,
                            value = prop.getter.call(data),
                            annotations = annotations
                        )
                    } ?: emptyList()
            }.toList().flatten()
        }
    }

    fun validate(
        name: String,
        value: Any?,
        annotations: NonEmptyList<Annotation>
    ): List<ValidationError.Message> {
        fun error(message: String) = Some(ValidationError.Message(name, message, value.toString()))

        fun check(message: String, isValid: () -> Boolean): Option<ValidationError.Message> =
            if (isValid()) None else error(message)

        fun checkString(message: String, isValid: (String) -> Boolean): Option<ValidationError.Message> =
            when (value) {
                is String -> check(message, { isValid(value) })
                is FileUpload -> check(message, { isValid(value.name) })
                else -> error("Not a string")
            }

        return annotations.toList().flatMap { annotation ->
            when (annotation) {
                is NotEmpty -> checkString("Value cannot be empty") { it.isNotEmpty() }
                is MinLength -> checkString("Minimum length is ${annotation.min} characters") { it.length >= annotation.min }
                is EmptyOrMinLength -> checkString("Minimum length is ${annotation.min} characters") { it.isEmpty() || it.length >= annotation.min }
                is MaxLength -> checkString("Maximum length is ${annotation.max} characters") { it.length <= annotation.max }
                is Min -> check("Minimum value is ${annotation.min}") { value as Int >= annotation.min }
                is Max -> check("Minimum value is ${annotation.max}") { value as Int <= annotation.max }
                is EmailAddress -> checkString("Invalid email address") { it.isEmpty() || it.isValidEmailAddress() }
                else -> None
            }.toList()
        }
    }
}

annotation class NotEmpty()
annotation class MinLength(val min: Int)
annotation class EmptyOrMinLength(val min: Int)
annotation class MaxLength(val max: Int)
annotation class Min(val min: Int)
annotation class Max(val max: Int)
annotation class EmailAddress()