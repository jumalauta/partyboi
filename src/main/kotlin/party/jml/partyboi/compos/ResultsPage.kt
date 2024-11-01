package party.jml.partyboi.compos

import party.jml.partyboi.templates.Page
import party.jml.partyboi.voting.CompoResult
import kotlinx.html.*

object ResultsPage {
    fun render(results: List<CompoResult>) = Page("Results") {
        h1 { +"Results" }
        CompoResult.groupResults(results).forEach { (compo, results) ->
            article {
                a { attributes["name"] = compo.id.toString() }
                header { +"${compo.name} compo" }
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
                                    td { +result.title }
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