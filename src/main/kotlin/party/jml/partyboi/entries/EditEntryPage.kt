package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import kotlinx.html.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.editEntryForm
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.components.icon
import java.time.format.DateTimeFormatter

object EditEntryPage {
    fun render(
        app: AppServices,
        formData: Form<EntryUpdate>,
        files: List<FileDesc>,
        screenshot: Option<String>,
    ): Either<AppError, Renderable> = either {
        val compos = app.compos.getAllCompos().bind()
        Page("Edit entry") {
            h1 { +"Edit entry" }
            editEntryForm("/entries/${formData.data.id}", compos.filter { it.allowSubmit }, formData)

            article {
                header { +"Screenshot / preview" }
                screenshot.fold(
                    { p { +"No screenshot uploaded" } },
                    { img(src = it) }
                )
                form(
                    method = FormMethod.post,
                    action = "/entries/${formData.data.id}/screenshot",
                    encType = FormEncType.multipartFormData
                ) {
                    fieldSet {
                        renderForm(Form(NewScreenshot::class, NewScreenshot.Empty, true))
                    }
                    footer {
                        submitInput { value = "Set screenshot" }
                    }
                }
            }

            if (files.isNotEmpty()) {
                article {
                    header { +"File versions" }
                    table {
                        thead {
                            tr {
                                th { +"Version" }
                                th { +"Name" }
                                th { +"Size" }
                                th { +"Uploaded" }
                                th {}
                            }
                        }
                        tbody {
                            files.forEach { file ->
                                tr {
                                    td { +file.version.toString() }
                                    td { +file.originalFilename }
                                    td { +Filesize.humanFriendly(file.size) }
                                    td { +file.uploadedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
                                    td {
                                        a(href="/entries/${file.entryId}/download/${file.version}") {
                                            icon("download")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}