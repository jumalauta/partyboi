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
            val isManual = results.any { it.results.any { r -> r.isManual } }
            val hasScores = isManual && results.any { it.results.any { r -> !r.scoreText.isNullOrBlank() } }
            val hasTitles = !isManual || results.any { it.results.any { r -> r.title.isNotBlank() } }

            article {
                a { attributes["name"] = compo.id.toString() }
                cardHeader(compo.name.withCompoSuffix())
                table {
                    thead {
                        tr {
                            th(classes = "narrow") { +"Place" }
                            th { +"Author" }
                            if (hasTitles) th { +"Title" }
                            if (isManual) {
                                if (hasScores) th { +"Score" }
                            } else {
                                th { +"Points" }
                            }
                        }
                    }
                    tbody {
                        results.forEach { (place, resultsForPlace) ->
                            resultsForPlace.forEachIndexed { index, result ->
                                tr {
                                    td { if (index == 0) +"$place." }
                                    td { +result.author }
                                    if (hasTitles) td {
                                        if (result.downloadLink != null) {
                                            a(href = result.downloadLink) {
                                                +result.title
                                            }
                                        } else {
                                            +result.title
                                        }
                                    }
                                    if (isManual) {
                                        if (hasScores) td { +(result.scoreText ?: "") }
                                    } else {
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
}