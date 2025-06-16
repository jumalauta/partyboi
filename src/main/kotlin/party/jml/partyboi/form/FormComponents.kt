package party.jml.partyboi.form

import arrow.core.Option
import io.ktor.util.logging.*
import kotlinx.html.*
import party.jml.partyboi.data.UserError
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.randomShortId
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.components.tooltip
import kotlin.enums.enumEntries

fun FlowContent.dataForm(url: String, block: FORM.() -> Unit) {
    form(action = url, method = FormMethod.post, encType = FormEncType.multipartFormData) {
        block()
    }
}

fun TABLE.readonlyField(label: String, value: String) {
    tr {
        th { small { +label } }
        td { +if (value.isNotEmpty()) value else "–" }
    }
}

fun <T : Validateable<T>> FlowContent.renderReadonlyFields(
    form: Form<T>,
    options: Map<String, List<DropdownOptionSupport>>? = null
) {
    table {
        form.forEach { data ->
            if (data.type == InputType.file) {
                // Render nothing
            } else if (data.type == InputType.hidden) {
                // Render nothing
            } else if (data.type == InputType.checkBox) {
                readonlyField(data.label, if (data.value.isNotEmpty()) "True" else "False")
            } else {
                val opts = options?.get(data.key)
                if (opts == null) {
                    if (data.presentation == FieldPresentation.large) {
                        if (data.value.isNotEmpty()) {
                            tr {
                                th {
                                    colSpan = "2"
                                    small { +data.label }
                                }
                            }
                            tr {
                                td {
                                    colSpan = "2"
                                    +data.value
                                }
                            }
                        }
                    } else {
                        readonlyField(data.label, data.value)
                    }
                } else {
                    val opt = opts
                        .map { it.toDropdownOption() }
                        .find { it.value == data.value }
                    readonlyField(data.label, opt?.label ?: data.value)
                }
            }
        }
    }
}

fun <T : Validateable<T>> FIELDSET.renderFields(
    form: Form<T>,
    options: Map<String, List<DropdownOptionSupport>>? = null
) {
    if (form.error != null) {
        section(classes = "error") {
            if (form.error is UserError) {
                +form.error.message
            } else {
                val id = randomShortId()
                KtorSimpleLogger("renderFields").error(
                    "Error $id: ${form.error.javaClass.simpleName} ${form.error.message}",
                    form.error.throwable
                )
                +"Something went wrong"
                span {
                    tooltip("Error id for debugging: $id")
                    +"..."
                }
            }

        }
    }
    form.forEach { data ->
        if (data.type == InputType.file) {
            formFileInput(data)
        } else if (data.type == InputType.hidden) {
            formHiddenValue(data)
        } else if (data.type == InputType.checkBox) {
            formCheckBox(data)
        } else {
            val opts = options?.get(data.key)
            if (opts == null) {
                when (data.presentation) {
                    FieldPresentation.large -> formTextArea(data, monospace = false)
                    FieldPresentation.monospace -> formTextArea(data, monospace = true)
                    else -> formTextInput(data)
                }
            } else {
                dropdown(data, opts)
            }
        }
    }
}

fun FlowContent.formDescription(description: Option<String>) {
    description.map { small(classes = "description") { +it } }
}

fun FlowContent.formError(error: Option<String>) {
    error.map { small(classes = "error") { +it } }
}

inline fun FlowOrInteractiveOrPhrasingContent.formTextInput(
    data: Form.FieldData,
    crossinline block: INPUT.() -> Unit = {}
) {
    label {
        span { +data.label }
        textInput(name = data.key) {
            value = data.value
            type = data.type
            block()
        }
        formDescription(data.description)
        formError(data.error)
    }
}

fun FlowOrInteractiveOrPhrasingContent.formTextArea(data: Form.FieldData, monospace: Boolean) {
    label {
        span { +data.label }
        div(classes = "grow-wrap") {
            attributes["data-replicated-value"] = data.value
            textArea(classes = if (monospace) "monospace" else null) {
                name = data.key
                onInput = "this.parentNode.dataset.replicatedValue = this.value"
                +data.value
            }
        }
        formDescription(data.description)
        formError(data.error)
    }
}

fun FlowOrInteractiveOrPhrasingContent.formHiddenValue(data: Form.FieldData) {
    hiddenInput(name = data.key) {
        value = data.value
    }
}

fun FlowOrInteractiveOrPhrasingContent.formFileInput(data: Form.FieldData) {
    label {
        span { +data.label }
        fileInput(name = data.key)
        formDescription(data.description)
        formError(data.error)
    }
}

fun FlowOrInteractiveOrPhrasingContent.formCheckBox(data: Form.FieldData) {
    input(name = data.key) {
        type = InputType.checkBox
        role = "switch"
        checked = data.value.isNotEmpty()
        +data.label
    }
}

fun FlowOrInteractiveOrPhrasingContent.dropdown(data: Form.FieldData, options: List<DropdownOptionSupport>) {
    label {
        span { +data.label }
        select {
            name = data.key
            options.map { opt ->
                val ddOpt = opt.toDropdownOption()
                option {
                    value = ddOpt.value
                    selected = ddOpt.value == data.value
                    if (ddOpt.dataFields != null) {
                        ddOpt.dataFields.forEach { (key, value) ->
                            if (value != null) {
                                attributes["data-$key"] = value
                            }
                        }
                    }
                    +ddOpt.label
                }
            }
        }
        formDescription(data.description)
        formError(data.error)
    }
}

interface DropdownOptionSupport {
    fun toDropdownOption(): DropdownOption
}

data class DropdownOption(
    val value: String,
    val label: String,
    val dataFields: Map<String, String?>? = null,
) : DropdownOptionSupport {
    override fun toDropdownOption(): DropdownOption = this

    companion object {
        val fromString: (String) -> DropdownOption = { DropdownOption(it, it) }
        inline fun <reified T : Enum<T>> fromEnum(toLabel: (T) -> String) =
            enumEntries<T>().map { DropdownOption(it.name, toLabel(it)) }
    }
}

fun FlowContent.switchLink(
    toggled: Boolean,
    labelOn: String,
    labelOff: String,
    urlPrefix: String,
    disable: Boolean = false
) {
    span {
        input {
            type = InputType.checkBox
            role = "switch"
            checked = toggled
            onClick = Javascript.build {
                httpPut("$urlPrefix/${!toggled}")
                refresh()
            }
            disabled = disable
            +(if (toggled) labelOn else labelOff)
        }
    }
}

fun <T : Validateable<T>> FlowContent.renderForm(
    url: String,
    form: Form<T>,
    title: String? = null,
    submitButtonLabel: String = "Save changes",
    options: Map<String, List<DropdownOptionSupport>>? = null
) {
    dataForm(url) {
        article {
            if (title != null) header { +title }
            fieldSet { renderFields(form, options) }
            submitButton(submitButtonLabel)
        }
    }
}

fun FlowContent.submitButton(label: String) {
    footer {
        submitInput { value = label }
    }
}