package party.jml.partyboi.voting

import kotlinx.html.*
import party.jml.partyboi.entries.VoteableEntry
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page

object UserVotingPage {
    fun render(entries: List<VoteableEntry>) =
        Page("Voting") {
            h1 { +"Voting" }

            if (entries.isEmpty()) {
                article { +"Nothing to vote at the moment." }
            }

            entries.groupBy { it.compoId }.forEach {
                article {
                    header { +it.value.first().compoName }
                    section {
                        table {
                            tbody {
                                it.value.forEachIndexed { index, entry ->
                                    tr {
                                        td { +"${index + 1}." }
                                        th { +"${entry.author} – ${entry.title}" }
                                        td {
                                            fieldSet {
                                                val entryId = "entry-${entry.entryId}"
                                                for (i in VoteService.POINT_RANGE) {
                                                    val inputId = "$entryId-$i"
                                                    input {
                                                        type = InputType.radio
                                                        id = inputId
                                                        name = "entry-${entry.entryId}"
                                                        checked = entry.points.getOrNull() == i
                                                        onClick = Javascript.build {
                                                            httpPut("/vote/${entry.entryId}/$i")
                                                        }
                                                    }
                                                    label {
                                                        htmlFor = inputId
                                                        +i.toString()
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