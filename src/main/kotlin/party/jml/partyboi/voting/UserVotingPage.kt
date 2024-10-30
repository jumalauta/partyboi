package party.jml.partyboi.voting

import kotlinx.html.*
import party.jml.partyboi.entries.VoteableEntry
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.Page

object UserVotingPage {
    fun render(entries: List<VoteableEntry>) =
        Page("Voting") {
            entries.groupBy { it.compoId }.forEach {
                article {
                    header { +it.value.first().compoName }
                    section {
                        table {
                            tbody {
                                it.value.forEach {
                                    tr {
                                        td { +"${it.runOrder}." }
                                        th { +"${it.author} – ${it.title}" }
                                        td {
                                            fieldSet {
                                                val entryId = "entry-${it.entryId}"
                                                for (i in VoteService.POINT_RANGE) {
                                                    val inputId = "$entryId-$i"
                                                    input {
                                                        type = InputType.radio
                                                        id = inputId
                                                        name = "entry-${it.entryId}"
                                                        checked = it.points.getOrNull() == i
                                                        onClick = Javascript.build {
                                                            httpPut("/vote/${it.entryId}/$i")
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