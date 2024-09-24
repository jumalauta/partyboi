package party.jml.partyboi.submit

import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.NewEntry
import party.jml.partyboi.form.Form
import arrow.core.*

fun FlowContent.submitForm(url: String, compos: List<Compo>, values: Form<NewEntry> = Form(NewEntry::class, NewEntry.Empty)) {
    form(classes = "submitForm appForm", method = FormMethod.post, action = url, encType = FormEncType.multipartFormData) {
        values.forEach { label, key, v, error ->
            when (key) {
                "compoId" -> dropdown(label, key, compos.map { DropdownOption.fromCompo(it) }, v)
                "file" -> formFileInput(label, key, error)
                else -> formTextInput(label, key) { value = v }
            }
        }

        footer {
            submitInput { value = "Submit" }
        }
    }
}

inline fun FlowContent.formError(error: Option<String>) {
    error.map { div(classes = "error") { +it } }
}

inline fun FlowOrInteractiveOrPhrasingContent.formTextInput(labelText: String, name : String, error: Option<String> = None, crossinline block : INPUT.() -> Unit = {}) {
    label {
        span { +labelText }
        textInput(name = name, block = block)
        formError(error)
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.formFileInput(labelText: String, name : String, error: Option<String> = None) {
    label {
        span { +labelText }
        fileInput(name = name)
        formError(error)
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.dropdown(labelText: String, inputName : String, options: List<DropdownOption>, selectedValue: String? = null, error: Option<String> = None) {
    label {
        span { +labelText }
        select {
            name = inputName
            options.map { opt ->
                option {
                    value = opt.value
                    selected = opt.value == selectedValue
                    +opt.label
                }
            }
        }
        formError(error)
    }
}

data class DropdownOption(val value: String, val label: String) {
    companion object {
        fun fromCompo(compo: Compo): DropdownOption {
            return DropdownOption(compo.id.toString(), compo.name)
        }
    }
}
