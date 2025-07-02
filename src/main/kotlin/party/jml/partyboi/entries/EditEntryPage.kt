package party.jml.partyboi.entries

import arrow.core.Option
import kotlinx.html.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.system.displayDateTime
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonGroup
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.icon

object EditEntryPage {
    fun render(
        user: User,
        entryUpdateForm: Form<EntryUpdate>,
        screenshotForm: Form<NewScreenshot>,
        compos: List<Compo>,
        files: List<FileDesc>,
        screenshot: Option<String>,
        allowEdit: Boolean,
    ): Page {
        val title = if (!allowEdit) "Entry" else "Edit entry"

        return Page(title) {
            h1 { +title }

            columns(
                {
                    if (!allowEdit) {
                        entryDetails(compos, entryUpdateForm)
                    } else {
                        renderForm(
                            url = "/entries/${entryUpdateForm.data.id}",
                            form = entryUpdateForm,
                            options = mapOf("compoId" to compos),
                        )
                    }
                },
                {
                    screenshot.map {
                        figure { img(src = it) }
                    }
                    renderForm(
                        url = "/entries/${entryUpdateForm.data.id}/screenshot",
                        form = screenshotForm,
                        title = "Screenshot / preview",
                        submitButtonLabel = "Set screenshot"
                    )
                }
            )

            if (files.isNotEmpty()) {
                article(classes = "fileversions") {
                    header { +"File versions" }
                    table {
                        thead {
                            tr {
                                th { +"Version" }
                                th { +"Name" }
                                th { +"Size" }
                                th { +"Uploaded" }
                                th { +"Info" }
                                th {}
                            }
                        }
                        tbody {
                            files.forEach { file ->
                                tr(classes = if (file.processed) "processed" else null) {
                                    td { +file.version.toString() }
                                    td { +file.originalFilename }
                                    td { +Filesize.humanFriendly(file.size) }
                                    td { +file.uploadedAt.displayDateTime() }
                                    td { file.info?.let { +it } }
                                    td {
                                        buttonGroup {
                                            a(href = "/entries/${file.entryId}/download/${file.version}") {
                                                icon("download", "Download")
                                            }
                                            if (user.isAdmin) {
                                                a(
                                                    href = "/admin/host/${file.entryId}/${file.version}",
                                                    target = "_blank"
                                                ) {
                                                    icon("globe", "Open in browser")
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
    }
}