package party.jml.partyboi.form

import arrow.core.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.FormError
import party.jml.partyboi.errors.InternalServerError
import party.jml.partyboi.errors.ValidationError
import java.io.File
import java.io.InputStream
import kotlin.reflect.KClass
import kotlin.reflect.full.*

class Form<T : Validateable<T>>(val kclass: KClass<T>, val data: T) {
    fun validated() = data.validate()

    val errors: List<ValidationError.Message> by lazy {
        data.validate().fold({ it.errors }, { emptyList() })
    }

    fun forEach(block: (label: String, key: String, value: String, error: Option<String>) -> Unit) {
        kclass.memberProperties
            .flatMap { prop ->
                val field = prop.findAnnotation<Field>()
                if (field == null) emptyList() else listOf(field to prop)
            }
            .sortedBy { it.first.order }
            .forEach { pair ->
                val key = pair.second.name
                val value = pair.second.get(data)
                    .toOption()
                    .map { if (it is String) it else "" }
                    .getOrElse { "" }
                val error = errors
                    .filter { it.target == key }
                    .toNonEmptyListOrNone()
                    .map { it.joinToString { it.message } }
                block(pair.first.name, key, value, error)
            }
    }

    companion object {
        suspend inline fun <reified T: Validateable<T>> fromParameters(parameters: MultiPartData): Either<AppError, Form<T>> {
            return try {
                val ctor = T::class.primaryConstructor ?: throw NotImplementedError("Primary constructor missing")

                var stringParams: MutableMap<String, String> = mutableMapOf()
                val fileParams: MutableMap<String, FileUpload> = mutableMapOf()

                parameters.forEachPart { part ->
                    val name = part.name ?: throw Error("Anonymous parameters not supported")
                    when (part) {
                        is PartData.FormItem -> stringParams[name] = part.value
                        is PartData.FileItem -> {
                            fileParams[name] = FileUpload(
                                name = part.originalFileName ?: throw Error("File name missing for parameter '$name'"),
                                source = part.streamProvider()
                            )
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                val args: List<Any> = ctor.valueParameters.map {
                    val name = it.name ?: throw Error("Anonymous parameters not supported")

                    val stringValue by lazy { stringParams.get(name) ?: "" }

                    when (it.type.toString()) {
                        "kotlin.String" -> {
                            stringValue
                        }
                        "kotlin.Int" -> {
                            try { stringValue.toInt() } catch (_: NumberFormatException) { -1 }
                        }
                        "party.jml.partyboi.form.FileUpload" -> {
                            fileParams.get(name) ?: throw Error("File parameter '$name' not found")
                        }
                        else -> {
                            throw error("Unsupported data type on property '$name': ${it.type}")
                        }
                    }
                }
                val data = ctor.call(*args.toTypedArray())
                Form(T::class, data).right()
            } catch (e: Error) {
                FormError(e.message ?: e.toString()).left()
            }
        }
    }
}

annotation class Field(val order: Int, val name: String)

data class FileUpload(
    val name: String,
    val source: InputStream,
) {
    fun writeTo(path: String): Either<AppError, Unit> {
        return try {
            File("$path/$name").outputStream().use { out ->
                while (true) {
                    val bytes = source.readNBytes(1024)
                    if (bytes.isEmpty()) break
                    out.write(bytes)
                }
                source.close()
            }
            Unit.right()
        } catch (err: Error) {
            InternalServerError(err.message ?: err.toString()).left()
        }
    }

    companion object {
        val Empty = FileUpload("", InputStream.nullInputStream())
    }
}