package party.jml.partyboi.assets.admin

import kotlinx.html.*
import party.jml.partyboi.assets.Asset
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.deleteButton
import party.jml.partyboi.templates.components.confirmDelete

object AdminAssetsPage {
    fun render(assets: List<Asset>, error: String? = null) =
        Page("Assets") {
            h1 { +"Assets" }

            columns(
                if (assets.isNotEmpty()) {
                    {
                        val hasHidden = assets.any { it.isHidden }
                        val grouped = assets.groupBy { it.directory }
                        val sortedGroups = grouped.entries.sortedBy { it.key.lowercase() }

                        div {
                            id = "asset-list"
                            sortedGroups.forEach { (directory, files) ->
                                val allHidden = files.all { it.isHidden }
                                article(classes = if (allHidden) "hidden-asset" else null) {
                                    details {
                                        attributes["name"] = "asset-groups"
                                        if (directory.isEmpty()) {
                                            attributes["open"] = "open"
                                        }
                                        summary {
                                            span { +("/${directory}") }
                                            if (directory.isNotEmpty()) {
                                                deleteButton(
                                                    url = "/admin/assets-dir/$directory",
                                                    tooltipText = "Delete /$directory",
                                                    confirmation = confirmDelete("directory", "/$directory", cascade = "all ${files.size} files in it")
                                                )
                                            }
                                        }
                                        table {
                                            thead {
                                                tr {
                                                    th { +"Name" }
                                                    th(classes = "narrow align-right") { +"Size" }
                                                    th(classes = "narrow align-right") {}
                                                }
                                            }
                                            tbody {
                                                files.forEach { asset ->
                                                    tr(classes = if (asset.isHidden) "hidden-asset" else null) {
                                                        td {
                                                            a(href = "/assets/${asset}") {
                                                                title = asset.fullName
                                                                +asset.fileName
                                                            }
                                                        }
                                                        td(classes = "narrow align-right file-size") {
                                                            +asset.formattedSize
                                                        }
                                                        td {
                                                            deleteButton(
                                                                url = "/admin/assets/$asset",
                                                                tooltipText = "Delete ${asset.truncatedName}",
                                                                confirmation = confirmDelete("file", asset.toString())
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (hasHidden) {
                            val hiddenCount = assets.count { it.isHidden }
                            button(classes = "secondary outline") {
                                id = "toggle-hidden-assets"
                                onClick = """
                                    var list = document.getElementById('asset-list');
                                    var shown = list.classList.toggle('show-hidden');
                                    this.textContent = shown ? 'Hide $hiddenCount hidden files' : 'Show $hiddenCount hidden files';
                                """.trimIndent()
                                +"Show $hiddenCount hidden files"
                            }
                        }
                    }
                } else null
            ) {
                form(action = "/admin/assets", method = FormMethod.post, encType = FormEncType.multipartFormData) {
                    article {
                        header { +"Add assets" }
                        fieldSet {
                            if (error != null) {
                                section(classes = "error") { +error }
                            }
                            label {
                                span { +"Upload files" }
                                fileInput(name = "files") {
                                    multiple = true
                                }
                            }
                        }
                        submitButton("Add")
                    }
                }
            }
        }
}