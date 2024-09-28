package party.jml.partyboi.submit

import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.NewEntry
import party.jml.partyboi.form.Form
import arrow.core.*

fun FlowContent.submitForm(url: String, compos: List<Compo>, values: Form<NewEntry>) {
    form(classes = "submitForm appForm", method = FormMethod.post, action = url, encType = FormEncType.multipartFormData) {
        article {
            header {
                +"Submit a new entry"
            }
            fieldSet {
                values.forEach { data ->
                    when (data.key) {
                        "compoId" -> dropdown(data, compos.map { DropdownOption.fromCompo(it) })
                        "file" -> formFileInput(data)
                        else -> formTextInput(data)
                    }
                }
            }
            footer {
                submitInput { value = "Submit" }
            }
        }
    }
}

inline fun FlowContent.formError(error: Option<String>) {
    error.map { small(classes = "error") { +it } }
}

inline fun FlowOrInteractiveOrPhrasingContent.formTextInput(data: Form.FieldData, crossinline block : INPUT.() -> Unit = {}) {
    label {
        span { +data.label }
        textInput(name = data.key) {
            value = data.value
            block()
        }
        formError(data.error)
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.formFileInput(data: Form.FieldData) {
    label {
        span { +data.label }
        fileInput(name = data.key)
        formError(data.error)
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.dropdown(data: Form.FieldData, options: List<DropdownOption>) {
    label {
        span { +data.label }
        select {
            name = data.key
            options.map { opt ->
                option {
                    value = opt.value
                    selected = opt.value == data.value
                    +opt.label
                }
            }
        }
        formError(data.error)
    }
}

data class DropdownOption(val value: String, val label: String) {
    companion object {
        fun fromCompo(compo: Compo): DropdownOption {
            return DropdownOption(compo.id.toString(), compo.name)
        }
    }
}
