package party.jml.partyboi.form

import arrow.core.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import kotlinx.html.InputType
import party.jml.partyboi.Config
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.data.*
import party.jml.partyboi.entries.FileFormat
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

class Form<T : Validateable<T>>(
    val kclass: KClass<T>,
    val data: T,
    val initial: Boolean,
    val accumulatedValidationErrors: List<ValidationError.Message> = emptyList(),
    val error: AppError? = null,
) {
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
        kclass.memberProperties
            .flatMap { prop ->
                val field = prop.findAnnotation<Field>()
                if (field == null) emptyList() else listOf(field to prop)
            }
            .sortedBy { it.first.order }
            .forEach { pair ->
                val (meta, prop) = pair
                if (meta.presentation != FieldPresentation.custom) {
                    val key = prop.name
                    val (inputType, value) =
                        prop.get(data).toOption()
                            .map { a ->
                                when (a) {
                                    is String -> Pair(InputType.text, a)
                                    is Int -> Pair(InputType.number, a.toString())
                                    is Boolean -> Pair(InputType.checkBox, if (a) "on" else "")
                                    is LocalDateTime -> Pair(
                                        InputType.dateTimeLocal,
                                        a.format(DateTimeFormatter.ISO_DATE_TIME)
                                    )

                                    is FileUpload -> Pair(InputType.file, "")
                                    is Enum<*> -> Pair(InputType.text, a.name)
                                    else -> TODO("${a.javaClass.name} not supported as Form property")
                                }
                            }
                            .getOrElse { Pair(InputType.text, "") }
                    val error = errors
                        .filter { it.target == key }
                        .toNonEmptyListOrNone()
                        .map { it.joinToString { it.message } }
                    block(
                        FieldData(
                            label = meta.label,
                            key = key,
                            value = value,
                            error = error,
                            type = when (meta.presentation) {
                                FieldPresentation.hidden -> InputType.hidden
                                FieldPresentation.secret -> InputType.password
                                else -> inputType
                            },
                            presentation = meta.presentation,
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
    )

    companion object {
        suspend inline fun <reified T : Validateable<T>> fromParameters(parameters: MultiPartData): Either<AppError, Form<T>> {
            return try {
                val ctor = T::class.primaryConstructor ?: throw NotImplementedError("Primary constructor missing")

                val stringParams = MapCollector<String, String>()
                val fileParams: MutableMap<String, FileUpload> = mutableMapOf()

                parameters.forEachPart { part ->
                    val name = part.name ?: throw Error("Anonymous parameters not supported")
                    when (part) {
                        is PartData.FormItem -> {
                            stringParams.add(name, part.value)
                            part.dispose()
                        }

                        is PartData.FileItem -> {
                            fileParams[name] = FileUpload(
                                name = part.originalFileName ?: throw Error("File name missing for parameter '$name'"),
                                fileItem = part,
                            )
                        }

                        else -> part.dispose()
                    }
                }

                val args: List<Any> = ctor.valueParameters.map {
                    val name = it.name ?: throw Error("Anonymous parameters not supported")

                    val stringValue by lazy { stringParams.first(name) ?: "" }

                    when (it.type.toString()) {
                        "kotlin.String" -> {
                            stringValue
                        }

                        "kotlin.Int" -> {
                            try {
                                stringValue.toInt()
                            } catch (_: NumberFormatException) {
                                -1
                            }
                        }

                        "kotlin.Boolean" -> {
                            stringValue.isNotEmpty()
                        }

                        "party.jml.partyboi.form.FileUpload" -> {
                            fileParams.get(name) ?: throw Error("File parameter '$name' not found")
                        }

                        "java.time.LocalDateTime" -> {
                            LocalDateTime.parse(stringValue)
                        }

                        // TODO: Generic implementations of following cases
                        "kotlin.collections.List<party.jml.partyboi.entries.FileFormat>" -> {
                            stringParams.all(name).map { FileFormat.valueOf(it) }
                        }

                        "party.jml.partyboi.settings.admin.AutomaticVoteKeys" -> {
                            AutomaticVoteKeys.valueOf(stringValue)
                        }

                        else -> {
                            throw error("Unsupported data type on property '$name': ${it.type}")
                        }
                    }
                }
                val data = ctor.call(*args.toTypedArray())
                Form(T::class, data, initial = false).right()
            } catch (e: Error) {
                FormError(e.message ?: e.toString()).left()
            }
        }

        inline fun <reified T : Validateable<T>> empty(emptyData: T): Form<T> =
            Form(T::class, emptyData, initial = true)
    }
}

annotation class Field(
    val order: Int = 0,
    val label: String = "",
    val presentation: FieldPresentation = FieldPresentation.normal,
)

enum class FieldPresentation {
    normal,
    hidden,
    large,
    monospace,
    secret,
    custom,
}

data class FileUpload(
    val name: String,
    val fileItem: PartData.FileItem,
) {
    fun write(storageFilename: Path): Either<AppError, Unit> {
        return try {
            val source = fileItem.streamProvider()
            val file = Config.get().entryDir.resolve(storageFilename).toFile()
            File(file.parent).mkdirs()
            file.outputStream().use { out ->
                while (true) {
                    val bytes = source.readNBytes(1024)
                    if (bytes.isEmpty()) break
                    out.write(bytes)
                }
                source.close()
            }
            fileItem.dispose()
            Unit.right()
        } catch (err: Error) {
            InternalServerError(err).left()
        }
    }

    fun toByteArray(): ByteArray {
        val source = fileItem.streamProvider()
        val bytes = source.readAllBytes()
        fileItem.dispose()
        return bytes
    }

    val isDefined = name.isNotEmpty()

    companion object {
        val Empty = FileUpload(
            "", PartData.FileItem(
                { throw Error("Empty file") },
                { },
                Headers.Empty
            )
        )

        fun createTestData(filename: String, length: Int) = fromByteArray(
            filename,
            ByteArray(length)
        )

        fun fromResource(self: Any, filename: String) = self::class.java.getResource(filename)?.let {
            fromByteArray(
                Paths.get(filename).fileName.toString(),
                it.readBytes()
            )
        }

        fun fromByteArray(filename: String, bytes: ByteArray) = FileUpload(
            filename,
            PartData.FileItem(
                { ByteReadPacket(bytes, 0, bytes.size) },
                {},
                Headers.Empty
            )
        )
    }
}