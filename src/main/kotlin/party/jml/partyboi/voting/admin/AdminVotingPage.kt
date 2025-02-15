package party.jml.partyboi.voting.admin

import arrow.core.toOption
import party.jml.partyboi.voting.VoteKeyRow
import party.jml.partyboi.templates.Page
import kotlinx.html.*
import party.jml.partyboi.auth.User

object AdminVotingPage {
    fun render(voteKeys: List<VoteKeyRow>, users: List<User>) = Page("Voting settings") {
        voteKeys.groupBy { it.key.keyType.explain(null) }.forEach { (keyType, keys) ->
            renderVoteKeyTable("$keyType (${keys.size})", keys, users)
        }
        article {
            a("/admin/voting/generate") {
                role = "button"
                +"Generate new vote key tickets"
            }
        }
    }
}

fun FlowContent.renderVoteKeyTable(title: String, voteKeys: List<VoteKeyRow>, users: List<User>) {
    val sorted = voteKeys
        .map {
            Pair(
                it,
                it.userId.flatMap { userId ->
                    users.find { user -> user.id == userId }.toOption()
                }
            )
        }
        .sortedBy { it.second.fold({ "" }, { it.name.lowercase() }) }

    val keySets = sorted.mapNotNull { it.first.set }
    val includePrintLinks = keySets.isNotEmpty()

    details {
        summary {
            role = "button"
            +title
        }
        article {
            table {
                thead {
                    tr {
                        th { +"Key" }
                        th { +"Assigned to" }
                        if (includePrintLinks) th { +"Print" }
                    }
                }
                tbody {
                    sorted.forEach { (key, user) ->
                        tr {
                            td { +key.key.toString() }
                            td { user.map { a(href = "/admin/users/${it.id}") { +it.name } } }
                            if (includePrintLinks) {
                                td {
                                    if (key.set != null) {
                                        a(href = "/admin/voting/print/${key.set}") { +"Show ticket set '${key.set}'" }
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