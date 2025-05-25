package party.jml.partyboi.voting

import arrow.core.Option
import kotlinx.html.*
import party.jml.partyboi.entries.Screenshot
import party.jml.partyboi.entries.VotableEntry
import party.jml.partyboi.signals.SignalType
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.refreshOnSignal

object UserVotingPage {
    fun render(
        entries: List<VotableEntry>,
        screenshots: List<Screenshot>,
        liveVote: Option<LiveVoteState>,
    ) = Page("Voting") {
        refreshOnSignal(SignalType.vote)
        refreshOnSignal(SignalType.liveVote)

        h1 { +"Voting" }

        if (entries.isEmpty() && liveVote.isNone()) {
            article { +"Nothing to vote at the moment." }
        }

        liveVote.map { live ->
            if (live.entries.isEmpty()) {
                article { +"Live voting for ${live.compo.name} compo begins soon!" }
            }
        }

        entries.groupBy { it.compoId }.forEach { compo ->
            article {
                cardHeader(compo.value.first().compoName)
                section {
                    table(classes = "voting") {
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
                                    td(classes = "tight order") { +"${index + 1}." }
                                    td(classes = "screenshot") {
                                        figure {
                                            if (screenshot != null) {
                                                attributes["style"] =
                                                    "background-image: url(${screenshot.externalUrl()})"
                                            }
                                        }
                                    }
                                    th(classes = "wide title") { +"${entry.author} – ${entry.title}" }
                                    for (points in VoteService.POINT_RANGE) {
                                        td(classes = "tight center points") {
                                            voteButton(entry.id, points, entry.points.getOrNull() == points)
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

fun FlowContent.voteButton(entryId: Int, points: Int, selected: Boolean) {
    val inputId = "entry-$entryId-$points"
    input {
        type = InputType.radio
        id = inputId
        name = "entry-${entryId}"
        checked = selected
        onClick = Javascript.build {
            httpPut("/vote/${entryId}/$points")
        }
    }
    label {
        htmlFor = inputId
        +"$points"
    }
}