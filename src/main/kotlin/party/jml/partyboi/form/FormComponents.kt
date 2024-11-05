package party.jml.partyboi.form

import arrow.core.Option
import io.ktor.util.logging.*
import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.UserError
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.randomShortId
import party.jml.partyboi.entries.EntryUpdate
import party.jml.partyboi.entries.NewEntry
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.components.tooltip

fun FlowContent.submitNewEntryForm(url: String, openCompos: List<Compo>, values: Form<NewEntry>) {
    dataForm(url) {
        article {
            if (openCompos.isEmpty()) {
                +"Submitting is closed"
            } else {
                cardHeader("Submit a new entry")
                fieldSet {
                    renderFields(values, mapOf(
                        "compoId" to openCompos.map { it.toDropdownOption() }
                    ))
                }
                footer {
                    submitInput { value = "Submit" }
                }
            }
        }
    }
}

fun FlowContent.editEntryForm(url: String, compos: List<Compo>, values: Form<EntryUpdate>) {
    dataForm(url) {
        article {
            fieldSet {
                renderFields(values, mapOf("compoId" to compos))
            }
            footer {
                submitInput { value = "Save changes" }
            }
        }
    }
}

fun FlowContent.dataForm(url: String, block: FORM.() -> Unit) {
    form(action = url, method = FormMethod.post, encType = FormEncType.multipartFormData) {
        block()
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
                    "Error {}: {} {}",
                    id,
                    form.error.javaClass.simpleName,
                    form.error.message
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
                if (data.presentation == FieldPresentation.large) {
                    formTextArea(data)
                } else {
                    formTextInput(data)
                }
            } else {
                dropdown(data, opts)
            }
        }
    }
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
        formError(data.error)
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.formTextArea(data: Form.FieldData) {
    label {
        span { +data.label }
        textArea {
            name = data.key
            +data.value
        }
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
                    +ddOpt.label
                }
            }
        }
        formError(data.error)
    }
}

interface DropdownOptionSupport {
    fun toDropdownOption(): DropdownOption
}

data class DropdownOption(val value: String, val label: String) : DropdownOptionSupport {
    override fun toDropdownOption(): DropdownOption = this

    companion object {
        val fromString: (String) -> DropdownOption = { DropdownOption(it, it) }
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
