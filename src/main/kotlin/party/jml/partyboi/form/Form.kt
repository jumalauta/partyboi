package party.jml.partyboi.form

import arrow.core.Option
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNone
import io.ktor.http.content.*
import kotlinx.html.InputType
import party.jml.partyboi.data.*
import party.jml.partyboi.system.AppResult
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

class Form<T : Validateable<T>>(
    val kclass: KClass<T>,
    val data: T,
    val initial: Boolean = true,
    val accumulatedValidationErrors: List<ValidationError.Message> = emptyList(),
    val error: AppError? = null,
) {
    val schema = Schema(kclass)

    fun validated() = data.validate()

    val errors: List<ValidationError.Message> by lazy {
        if (initial) {
            accumulatedValidationErrors
        } else {
            data.validate().fold(
                { (accumulatedValidationErrors + it.errors).distinct() },
                { accumulatedValidationErrors }
            )
        }
    }

    fun forEach(block: (FieldData) -> Unit) {
        schema.properties.forEach { prop ->
            val meta = prop.meta
            if (meta.isDefined() && meta.presentation != FieldPresentation.custom) {
                val value = kclass.memberProperties.find { it.name == prop.name }?.get(data)
                val error = errors
                    .filter { it.target == prop.name }
                    .toNonEmptyListOrNone()
                    .map { it.joinToString { it.message } }

                block(
                    FieldData(
                        label = meta.label ?: prop.name.toLabel(),
                        key = prop.name,
                        value = prop.serialize(value),
                        error = error,
                        type = prop.getInputType(),
                        presentation = meta.presentation ?: FieldPresentation.normal,
                        description = meta.description.nonEmptyStringOption(),
                    )
                )
            }
        }
    }

    fun with(error: AppError): Form<T> {
        val errors = accumulatedValidationErrors + when (error) {
            is ValidationError -> error.errors
            else -> emptyList()
        }
        val uniqueErrors = errors.distinct()
        return Form(kclass, data, initial, uniqueErrors, if (error is ValidationError) null else error)
    }

    fun mapError(f: (AppError) -> AppError) = when (error) {
        null -> this
        else -> Form(kclass, data, initial, accumulatedValidationErrors, f(error))
    }

    data class FieldData(
        val label: String,
        val key: String,
        val value: String,
        val error: Option<String>,
        val type: InputType,
        val presentation: FieldPresentation,
        val description: Option<String>,
    )

    companion object {
        inline fun <reified T : Validateable<T>> of(obj: T): Form<T> = Form(T::class, obj)

        suspend inline fun <reified T : Validateable<T>> fromParameters(parameters: MultiPartData): AppResult<Form<T>> =
            try {
                val schema = Schema(T::class)
                val (strings, files) = parameters.collect()

                val data = schema.constructWith {
                    it.parse(
                        strings[it.name] ?: emptyList(),
                        files[it.name]?.map { it.fileItem } ?: emptyList(),
                    )
                } as T

                of(data).right()
            } catch (e: Error) {
                FormError(e.message ?: e.toString()).left()
            }
    }
}

enum class FieldPresentation {
    normal,
    hidden,
    large,
    monospace,
    secret,
    email,
    custom,
}

