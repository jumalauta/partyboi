package party.jml.partyboi.form

import arrow.core.Option
import arrow.core.none
import io.ktor.http.content.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.html.InputType
import party.jml.partyboi.system.TimeService
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

data class Schema(
    val kclass: KClass<*>,
) {
    private val ctor = kclass.primaryConstructor ?: throw NotImplementedError("No primary constructor")

    val properties: List<Property> by lazy {
        ctor.parameters.mapNotNull { param ->
            param.name?.let { name ->
                val annotation = param.annotations.fold(
                    PropertyMeta()
                ) { meta, annotation ->
                    when (annotation) {
                        is Field -> meta.copy(
                            label = annotation.label,
                            description = annotation.description,
                            presentation = annotation.presentation
                        )

                        is Label -> meta.copy(label = annotation.text)
                        is Description -> meta.copy(description = annotation.text)
                        is Presentation -> meta.copy(presentation = annotation.presentation)
                        is Hidden -> meta.copy(presentation = FieldPresentation.hidden)
                        is Large -> meta.copy(presentation = FieldPresentation.large)
                        is Custom -> meta.copy(presentation = FieldPresentation.custom)
                        else -> meta
                    }
                }

                Property.formValueToProperty(
                    type = param.type,
                    meta = annotation,
                    name = name,
                )
            }
        }
    }

    fun constructWith(getValue: (Property) -> Any?): Any {
        val args = properties.map { getValue(it) }.toTypedArray()
        return try {
            ctor.call(*args)
        } catch (e: Throwable) {
            println("Could not construct ${ctor} with args ${args.toList()}")
            throw e
        }
    }
}

data class PropertyMeta(
    val label: String? = null,
    val description: String? = null,
    val presentation: FieldPresentation? = null,
) {
    fun isDefined(): Boolean = !isEmpty()
    fun isEmpty(): Boolean = label == null && description == null && presentation == null
}

sealed interface Property {
    val name: String
    val optional: Boolean
    val defaultValue: Any
    val defaultInputType: InputType
    val meta: PropertyMeta

    fun serialize(value: Any?): String = value.toString()
    fun parse(values: List<String>, files: List<PartData.FileItem>) =
        parseFormValue(values, files) ?: if (optional) null else defaultValue

    fun parseFormValue(values: List<String>, files: List<PartData.FileItem>): Any?

    fun getInputType() = when (meta.presentation) {
        FieldPresentation.hidden -> InputType.hidden
        FieldPresentation.secret -> InputType.password
        FieldPresentation.email -> InputType.email
        else -> defaultInputType
    }

    companion object {
        fun formValueToProperty(type: KType, meta: PropertyMeta, name: String): Property? {
            val optional = type.isMarkedNullable
            return when (type.classifier) {
                String::class -> TextProp(name, optional, meta)
                Int::class -> IntProp(name, optional, meta)
                Boolean::class -> BooleanProp(name, optional, meta)
                Instant::class -> InstantProp(name, optional, meta)
                FileUpload::class -> FileUploadProp(name, optional, meta)
                TimeZone::class -> TimeZoneProp(name, optional, meta)
                Option::class -> type.arguments.first().type?.let {
                    formValueToProperty(it, meta, name)
                        ?.let { OptionProp(it, optional) }
                }

                List::class -> type.arguments.first().type?.let {
                    formValueToProperty(it, meta, name)
                        ?.let { ListProp(it, optional) }
                }

                else -> {
                    (type.classifier as? KClass<*>)?.let { classifier ->
                        classifier.enumValues()?.let { values ->
                            if (values.isNotEmpty()) {
                                EnumProp(name, optional, classifier, meta)
                            } else null
                        }
                    } ?: TODO("Unsupported type $type")
                }
            }
        }
    }
}

sealed interface SelectProperty : Property {
    val options: List<Pair<String, String>>
}

abstract class WrapperProperty(val property: Property) : Property {
    override val name: String = property.name
    override val defaultInputType: InputType = property.defaultInputType
    override val meta: PropertyMeta = property.meta

    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,

        ): Any? =
        wrap(values.map { property.parseFormValue(listOf(it), files) })

    abstract fun wrap(values: List<Any?>): Any?
}

data class TextProp(
    override val name: String,
    override val optional: Boolean,
    override val meta: PropertyMeta,
) : Property {
    override val defaultValue = ""
    override val defaultInputType: InputType = InputType.text
    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,
    ): String? =
        values.firstOrNull()
}

data class IntProp(
    override val name: String,
    override val optional: Boolean,
    override val meta: PropertyMeta,
) : Property {
    override val defaultValue = 0
    override val defaultInputType: InputType = InputType.number
    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,

        ): Int? =
        values.firstOrNull()?.toInt()
}

data class BooleanProp(
    override val name: String,
    override val optional: Boolean,
    override val meta: PropertyMeta,
) : Property {
    override val defaultValue = false
    override val defaultInputType: InputType = InputType.checkBox
    override fun serialize(value: Any?): String = if (value is Boolean && value) "on" else ""
    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,

        ): Boolean? = when (val value = values.firstOrNull()) {
        "true" -> true
        "false" -> false
        "none" -> null
        else -> value?.isNotEmpty() ?: false
    }
}

data class InstantProp(
    override val name: String,
    override val optional: Boolean,
    override val meta: PropertyMeta,
) : Property {
    val format = DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET
    override val defaultValue = Clock.System.now()
    override val defaultInputType: InputType = InputType.dateTime
    override fun serialize(value: Any?): String =
        when (value) {
            null -> ""
            is Instant -> value.format(format)
            else -> TODO("Only Instant supported for datetime values. Unsupported type: $name $value")
        }

    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,
    ): Any? =
        values.firstOrNull()?.let {
            if (it.isEmpty()) null
            else Instant.parse(it, format)
        }

}

data class FileUploadProp(
    override val name: String,
    override val optional: Boolean,
    override val meta: PropertyMeta,
) : Property {
    override val defaultValue = FileUpload.Empty
    override val defaultInputType: InputType = InputType.file
    override fun serialize(value: Any?): String = ""
    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,

        ): Any? =
        files.firstOrNull()?.let { FileUpload(name, it) }
}

data class EnumProp(
    override val name: String,
    override val optional: Boolean,
    val type: KClass<*>,
    override val meta: PropertyMeta,
) : SelectProperty {
    override val defaultValue = type.enumValues()!!.first()
    override val defaultInputType: InputType = InputType.text
    override val options: List<Pair<String, String>> = type.enumValues()?.map { it.name to it.name } ?: emptyList()

    override fun serialize(value: Any?): String = (value as? Enum<*>)?.name ?: ""
    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,

        ): Enum<*>? =
        values.firstOrNull()?.let { name ->
            type.enumValues()?.firstOrNull { it.name == name }
        }
}

data class TimeZoneProp(
    override val name: String,
    override val optional: Boolean,
    override val meta: PropertyMeta,
) : SelectProperty {
    override val defaultValue = TimeService.timeZone()
    override val defaultInputType: InputType = InputType.text
    override val options = TimeZone.availableZoneIds.toList().map { it to it }

    override fun serialize(value: Any?): String = (value as? TimeZone)?.id ?: ""
    override fun parseFormValue(
        values: List<String>,
        files: List<PartData.FileItem>,

        ): TimeZone? =
        values.firstOrNull()?.let { TimeZone.of(it) }
}

class OptionProp(
    property: Property,
    override val optional: Boolean,
) : WrapperProperty(property) {
    override val defaultValue = none<Any>()
    override fun wrap(values: List<Any?>): Any? = Option.fromNullable(values.firstOrNull())
}

class ListProp(
    property: Property,
    override val optional: Boolean,
) : WrapperProperty(property) {
    override val defaultValue = emptyList<Any>()
    override fun wrap(values: List<Any?>): Any? = values
}

fun KClass<*>.enumValues(): List<Enum<*>>? =
    if (this.java.isEnum) {
        this.java.enumConstants.map { it as Enum<*> }
    } else {
        null
    }
