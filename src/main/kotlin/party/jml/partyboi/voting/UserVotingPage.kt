package party.jml.partyboi.voting

import kotlinx.html.*
import party.jml.partyboi.entries.Preview
import party.jml.partyboi.entries.VotableEntry
import party.jml.partyboi.signals.SignalType
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.components.reloadSection
import party.jml.partyboi.templates.refreshOnSignal
import java.util.*

object UserVotingPage {
    fun render(
        entries: List<VotableEntry>,
        previews: List<Preview>,
        liveVote: LiveVoteState?,
    ) = Page("Voting") {
        refreshOnSignal(SignalType.vote)
        refreshOnSignal(SignalType.liveVote)

        reloadSection {
            h1 { +"Voting" }

            if (entries.isEmpty() && liveVote == null) {
                article { +"Nothing to vote at the moment." }
            }

            liveVote?.let { live ->
                if (live.entries.isEmpty()) {
                    article { +"Live voting for ${live.compo.displayName} begins soon!" }
                }
            }

            entries.groupBy { it.compoId }.forEach { compo ->
                val isLiveVote = compo.value.first().compoId == liveVote?.compo?.id
                article {
                    cardHeader(compo.value.first().compoName)
                    section {
                        table(classes = "voting") {
                            thead {
                                tr {
                                    th(classes = "tight") { +"#" }
                                    if (!isLiveVote) th {}
                                    th(classes = "wide") { +"Author – Entry" }
                                    for (i in VoteService.POINT_RANGE) {
                                        th(classes = "tight center") { +i.toString() }
                                    }
                                }
                            }
                            tbody {
                                val entryCount = compo.value.size
                                compo.value.forEachIndexed { index, entry ->
                                    val preview = previews.find { it.entryId == entry.entryId }
                                    tr {
                                        td(classes = "tight order") {
                                            val entryIndex = if (isLiveVote) entryCount - index else index + 1
                                            +"$entryIndex."
                                        }
                                        if (!isLiveVote) {
                                            td(classes = "screenshot") {
                                                if (preview != null) {
                                                    val isVideo = preview.previewFileIsVideo
                                                    figure(classes = "clickable-preview") {
                                                        attributes["style"] =
                                                            "background-image: url(${preview.externalUrl()})"
                                                        attributes["data-preview-url"] =
                                                            preview.externalPreviewFileUrl()
                                                        attributes["data-preview-type"] =
                                                            if (isVideo) "video" else "image"
                                                        attributes["role"] = "button"
                                                        attributes["tabindex"] = "0"
                                                        attributes["aria-label"] = "Open full-size preview"
                                                    }
                                                } else {
                                                    figure {}
                                                }
                                            }
                                        }
                                        th(classes = "wide title") {
                                            if (entry.hideAuthor) {
                                                +entry.title
                                            } else {
                                                +"${entry.author} – ${entry.title}"
                                            }
                                            entry.info?.let { small(classes = "entry-info") { +it } }
                                        }
                                        for (points in VoteService.POINT_RANGE) {
                                            td(classes = "tight center points") {
                                                voteButton(entry.id, points, entry.points == points)
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

fun FlowContent.voteButton(entryId: UUID, points: Int, selected: Boolean) {
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