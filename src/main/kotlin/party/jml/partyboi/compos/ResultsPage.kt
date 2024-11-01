package party.jml.partyboi.compos

import party.jml.partyboi.templates.Page
import party.jml.partyboi.voting.CompoResult
import kotlinx.html.*

object ResultsPage {
    fun render(results: List<CompoResult>) = Page("Results") {
        h1 { +"Results" }
        results.groupBy { it.compoName }.forEach { (compoName, results) ->
                article {
                    a { attributes["name"] = results.first().compoId.toString() }
                    header { +"$compoName compo" }
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
                            var place = 1
                            results.groupBy { it.points }.forEach { (_, resultsForPlace) ->
                                resultsForPlace.forEachIndexed { index, result ->
                                    tr {
                                        td { if (index == 0) +"$place." }
                                        td { +result.author }
                                        td { +result.title }
                                        td { +result.points.toString() }
                                    }
                                }
                                place += resultsForPlace.size
                            }
                        }
                    }
                }
            }
    }
}