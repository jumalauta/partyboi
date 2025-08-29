package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.voting.CompoResult

object ResultsPage {
    fun render(results: List<CompoResult>) = Page("Results") {
        h1 { +"Results" }

        if (results.isEmpty()) {
            article {
                +"No results available yet!"
            }
        }

        CompoResult.groupResults(results).forEach { (compo, results) ->
            article {
                a { attributes["name"] = compo.id.toString() }
                cardHeader("${compo.name} compo")
                table {
                    thead {
                        tr {
                            th(classes = "narrow") { +"Place" }
                            th { +"Author" }
                            th { +"Title" }
                            th { +"Points" }
                        }
                    }
                    tbody {
                        results.forEach { (place, resultsForPlace) ->
                            resultsForPlace.forEachIndexed { index, result ->
                                tr {
                                    td { if (index == 0) +"$place." }
                                    td { +result.author }
                                    td {
                                        if (result.downloadLink != null) {
                                            a(href = result.downloadLink) {
                                                +result.title
                                            }
                                        } else {
                                            +result.title
                                        }
                                    }
                                    td { +result.points.toString() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}