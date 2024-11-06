package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import kotlinx.html.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.icon
import java.time.format.DateTimeFormatter

object EditEntryPage {
    fun render(
        entryUpdateForm: Form<EntryUpdate>,
        screenshotForm: Form<NewScreenshot>,
        compos: List<Compo>,
        files: List<FileDesc>,
        screenshot: Option<String>,
    ): Page {
        val isFrozen = compos.find { it.id == entryUpdateForm.data.compoId }?.allowSubmit != true
        val title = if (isFrozen) "Entry" else "Edit entry"

        return Page(title) {
            h1 { +title }

            columns(
                {
                    if (isFrozen) {
                        entryDetails(compos, entryUpdateForm)
                    } else {
                        editEntryForm(
                            "/entries/${entryUpdateForm.data.id}",
                            compos,
                            entryUpdateForm
                        )
                    }
                },
                {
                    screenshot.map {
                        figure { img(src = it) }
                    }
                    article {
                        header { +"Screenshot / preview" }
                        dataForm("/entries/${entryUpdateForm.data.id}/screenshot") {
                            fieldSet {
                                renderFields(screenshotForm)
                            }
                            footer {
                                submitInput { value = "Set screenshot" }
                            }
                        }
                    }
                }
            )

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
                                        a(href = "/entries/${file.entryId}/download/${file.version}") {
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