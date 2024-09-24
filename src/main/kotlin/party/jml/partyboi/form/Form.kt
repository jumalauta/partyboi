package party.jml.partyboi.form

import arrow.core.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.FormError
import party.jml.partyboi.errors.InternalServerError
import party.jml.partyboi.errors.ValidationError
import java.io.File
import kotlin.io.use
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
                val fileParams: MutableMap<String, PartData.FileItem> = mutableMapOf()

                parameters.forEachPart { part ->
                    val name = part.name ?: throw Error("Anonymous parameters not supported")
                    when (part) {
                        is PartData.FormItem -> {
                            stringParams[name] = part.value
                            part.dispose()
                        }
                        is PartData.FileItem -> fileParams[name] = part
                        is PartData.BinaryItem -> {
                            part.dispose()
                            throw Error("Binary parameters not supported")
                        }
                        is PartData.BinaryChannelItem -> {
                            part.dispose()
                            throw Error("Binary channel parameters not supported")
                        }
                    }
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
                            val part = fileParams.get(name) ?: throw Error("File parameter '$name' not found")
                            FileUpload(
                                name = part.originalFileName ?: throw Error("File name missing for parameter '$name'"),
                                part = part
                            )
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
    val part: PartData.FileItem
) {
    suspend fun writeTo(path: String): Either<AppError, Unit> {
        return try {
            val source = part.provider().readRemaining()
            val out = File("$path/$name")
            source.read { buf -> out.appendBytes(buf.array()) }
            Unit.right()
        } catch (err: Error) {
            InternalServerError(err.message ?: err.toString()).left()
        } finally {
            part.dispose()
        }
    }

    companion object {
        val Empty = FileUpload("", PartData.FileItem( { TODO("") }, { TODO("") }, Headers.Empty ))
    }
}