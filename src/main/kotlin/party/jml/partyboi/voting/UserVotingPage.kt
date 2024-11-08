package party.jml.partyboi.voting

import kotlinx.html.*
import party.jml.partyboi.entries.Screenshot
import party.jml.partyboi.entries.VotableEntry
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader

object UserVotingPage {
    fun render(entries: List<VotableEntry>, screenshots: List<Screenshot>) =
        Page("Voting") {
            h1 { +"Voting" }

            if (entries.isEmpty()) {
                article { +"Nothing to vote at the moment." }
            }

            entries.groupBy { it.compoId }.forEach { compo ->
                article {
                    cardHeader(compo.value.first().compoName)
                    section {
                        table {
                            thead {
                                tr {
                                    th(classes = "tight") { +"#" }
                                    th {}
                                    th(classes = "wide") { +"Author – Entry" }
                                    for (i in VoteService.POINT_RANGE) {
                                        th(classes = "tight center") { +i.toString() }
                                    }
                                }
                            }
                            tbody {
                                compo.value.forEachIndexed { index, entry ->
                                    val screenshot = screenshots.find { it.entryId == entry.entryId }
                                    tr {
                                        td(classes = "tight") { +"${index + 1}." }
                                        td(classes = "screenshot") {
                                            figure {
                                                if (screenshot != null) {
                                                    attributes["style"] =
                                                        "background-image: url(${screenshot.externalUrl()})"
                                                }
                                            }
                                        }
                                        th(classes = "wide") { +"${entry.author} – ${entry.title}" }
                                        val entryId = "entry-${entry.entryId}"
                                        for (i in VoteService.POINT_RANGE) {
                                            val inputId = "$entryId-$i"
                                            td(classes = "tight center") {
                                                input {
                                                    type = InputType.radio
                                                    id = inputId
                                                    name = "entry-${entry.entryId}"
                                                    checked = entry.points.getOrNull() == i
                                                    onClick = Javascript.build {
                                                        httpPut("/vote/${entry.entryId}/$i")
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