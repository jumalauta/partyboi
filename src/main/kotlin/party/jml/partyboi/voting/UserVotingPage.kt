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
                    article { +"Live voting for ${live.compo.displayName} begins soon." }
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
                                                    val hasAudio = preview.previewAudioFilePath != null
                                                    val classes = buildString {
                                                        append("clickable-preview")
                                                        if (hasAudio) append(" has-audio")
                                                    }
                                                    figure(classes = classes) {
                                                        attributes["style"] =
                                                            "background-image: url(${preview.externalUrl()})"
                                                        attributes["data-preview-url"] =
                                                            preview.externalPreviewFileUrl()
                                                        attributes["data-preview-type"] =
                                                            if (isVideo) "video" else "image"
                                                        if (hasAudio) {
                                                            attributes["data-preview-audio-url"] =
                                                                preview.externalPreviewAudioFileUrl()
                                                        }
                                                        attributes["role"] = "button"
                                                        attributes["tabindex"] = "0"
                                                        attributes["aria-label"] =
                                                            if (hasAudio) "Play audio preview" else "Open full-size preview"
                                                        if (hasAudio) {
                                                            i(classes = "fa-solid fa-circle-play play-overlay") {}
                                                        }
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

            article {
                cardHeader("How voting works")
                section {
                    ul {
                        li {
                            +"Rate each entry from ${VoteService.MIN_POINTS} (worst) to ${VoteService.MAX_POINTS} (best). Click a number — your vote is saved immediately."
                        }
                        li {
                            +"You can change your vote at any time while the compo is open for voting."
                        }
                        li {
                            +"Once you have voted at least one entry in a compo, every other entry in that compo automatically receives ${VoteService.MEAN_POINTS} points from you. Skipping an entry is not the same as voting against it."
                        }
                        li {
                            +"If you do not vote any entry in a compo, you contribute no points there."
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