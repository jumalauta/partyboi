package party.jml.partyboi.sync

import kotlinx.html.*
import kotlinx.datetime.TimeZone
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.components.timestamp

object ReconcilePage {
    fun render(
        entryCandidates: List<DuplicateEntryCandidate>,
        userCandidates: List<DuplicateUserCandidate>,
        tz: TimeZone,
    ) = Page("Reconciliation") {
        h1 { +"Reconciliation" }

        p {
            +"When two instances both accept submissions during a sync split, the same person or "
            +"prod can end up as two unrelated rows after a merge. The pairs below look like such "
            +"duplicates. Merging keeps one copy and moves the other's files and votes onto it; "
            +"when the same voter voted on both copies, the higher points win. "
            strong { +"Merging cannot be undone." }
        }

        if (userCandidates.isEmpty() && entryCandidates.isEmpty()) {
            article {
                p { +"No duplicate candidates found." }
            }
        }

        if (userCandidates.isNotEmpty()) {
            h2 { +"Users" }
            p {
                small {
                    +"Merge users before entries: entry ownership and ballots follow the user. "
                    +"The kept account's password stays; the other account is deleted."
                }
            }
            userCandidates.forEach { candidate ->
                article {
                    cardHeader("${candidate.a.name} / ${candidate.b.name}")
                    p {
                        small {
                            +(if (candidate.emailMatch) "Same email address" else "Similar name")
                        }
                    }
                    table {
                        thead {
                            tr {
                                th {}
                                th { +"Copy A" }
                                th { +"Copy B" }
                            }
                        }
                        tbody {
                            tr {
                                th { +"Name" }
                                td { +candidate.a.name }
                                td { +candidate.b.name }
                            }
                            tr {
                                th { +"Email" }
                                td { +(candidate.a.email ?: "—") }
                                td { +(candidate.b.email ?: "—") }
                            }
                            tr {
                                th { +"Origin" }
                                td { +(candidate.a.origin ?: "unknown") }
                                td { +(candidate.b.origin ?: "unknown") }
                            }
                            tr {
                                th { +"Entries" }
                                td { +candidate.a.entryCount.toString() }
                                td { +candidate.b.entryCount.toString() }
                            }
                            tr {
                                th { +"Votes cast" }
                                td { +candidate.a.voteCount.toString() }
                                td { +candidate.b.voteCount.toString() }
                            }
                            tr {
                                th {}
                                td {
                                    form(
                                        "/sync/reconcile/user/${candidate.a.id}/merge/${candidate.b.id}",
                                        method = FormMethod.post
                                    ) { submitButton("Keep this account") }
                                }
                                td {
                                    form(
                                        "/sync/reconcile/user/${candidate.b.id}/merge/${candidate.a.id}",
                                        method = FormMethod.post
                                    ) { submitButton("Keep this account") }
                                }
                            }
                        }
                    }
                    form(
                        "/sync/reconcile/user/${candidate.a.id}/dismiss/${candidate.b.id}",
                        method = FormMethod.post
                    ) { submitButton("Not a duplicate") }
                }
            }
        }

        if (entryCandidates.isNotEmpty()) {
            h2 { +"Entries" }
            entryCandidates.forEach { candidate ->
                article {
                    cardHeader("${candidate.compoName}: ${candidate.a.title} by ${candidate.a.author}")
                    p {
                        small {
                            +(if (candidate.checksumMatch) "Identical file contents" else "Same title and author")
                        }
                    }
                    table {
                        thead {
                            tr {
                                th {}
                                th { +"Copy A" }
                                th { +"Copy B" }
                            }
                        }
                        tbody {
                            tr {
                                th { +"Title" }
                                td { +candidate.a.title }
                                td { +candidate.b.title }
                            }
                            tr {
                                th { +"Author" }
                                td { +candidate.a.author }
                                td { +candidate.b.author }
                            }
                            tr {
                                th { +"Submitted by" }
                                td { +candidate.a.userName }
                                td { +candidate.b.userName }
                            }
                            tr {
                                th { +"Origin" }
                                td { +(candidate.a.origin ?: "unknown") }
                                td { +(candidate.b.origin ?: "unknown") }
                            }
                            tr {
                                th { +"Submitted" }
                                td { timestamp(candidate.a.timestamp, tz) }
                                td { timestamp(candidate.b.timestamp, tz) }
                            }
                            tr {
                                th { +"Votes" }
                                td { +candidate.a.voteCount.toString() }
                                td { +candidate.b.voteCount.toString() }
                            }
                            tr {
                                th { +"Files" }
                                td { +candidate.a.fileCount.toString() }
                                td { +candidate.b.fileCount.toString() }
                            }
                            tr {
                                th {}
                                td {
                                    form(
                                        "/sync/reconcile/entry/${candidate.a.id}/merge/${candidate.b.id}",
                                        method = FormMethod.post
                                    ) { submitButton("Keep this copy") }
                                }
                                td {
                                    form(
                                        "/sync/reconcile/entry/${candidate.b.id}/merge/${candidate.a.id}",
                                        method = FormMethod.post
                                    ) { submitButton("Keep this copy") }
                                }
                            }
                        }
                    }
                    form(
                        "/sync/reconcile/entry/${candidate.a.id}/dismiss/${candidate.b.id}",
                        method = FormMethod.post
                    ) { submitButton("Not a duplicate") }
                }
            }
        }

        a(href = "/sync") { +"Back to sync" }
    }
}
