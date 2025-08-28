package party.jml.partyboi.assets.admin

import kotlinx.html.*
import party.jml.partyboi.assets.Asset
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Label
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.deleteButton
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable

object AdminAssetsPage {
    fun render(addAssetForm: Form<AddAsset>, assets: List<Asset>) =
        Page("Assets") {
            h1 { +"Assets" }

            columns(
                if (assets.isNotEmpty()) {
                    {
                        article {
                            header { +"Assets" }
                            table {
                                thead {
                                    tr {
                                        th { +"Name" }
                                        th(classes = "narrow align-right") {}
                                    }
                                }
                                tbody {
                                    assets.forEach {
                                        tr {
                                            td {
                                                a(href = "/assets/${it}") {
                                                    title = it.fullName
                                                    +it.truncatedName
                                                }
                                            }
                                            td {
                                                deleteButton(
                                                    url = "/admin/assets/$it",
                                                    tooltipText = "Delete ${it.truncatedName}",
                                                    confirmation = "Do you really want to delete file $it?"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else null
            ) {
                renderForm(
                    url = "/admin/assets",
                    form = addAssetForm,
                    title = "Add asset",
                    submitButtonLabel = "Add"
                )
            }
        }

    data class AddAsset(
        @Label("Upload file")
        @NotEmpty
        val file: FileUpload
    ) : Validateable<AddAsset> {
        companion object {
            val Empty = AddAsset(FileUpload.Empty)
        }
    }
}