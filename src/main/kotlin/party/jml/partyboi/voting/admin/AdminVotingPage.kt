package party.jml.partyboi.voting.admin

import arrow.core.toOption
import kotlinx.html.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.settings.VoteSettings
import party.jml.partyboi.templates.Page
import party.jml.partyboi.voting.VoteKeyRow

object AdminVotingPage {
    fun render(
        voteKeys: List<VoteKeyRow>,
        users: List<User>,
        settings: Form<VoteSettings>
    ) = Page("Voting settings") {
        renderForm(
            url = "/admin/voting/settings",
            form = settings,
            title = "Settings",
            options = mapOf(
                "automaticVoteKeys" to DropdownOption.fromEnum<AutomaticVoteKeys> { it.label }
            )
        )

        article {
            header { +"Create new vote keys" }
            a("/admin/voting/generate") {
                role = "button"
                +"Generate new vote key tickets"
            }
        }

        article {
            header { +"Browse existing vote keys" }
            voteKeys.groupBy { it.key.keyType.explain(null) }.forEach { (keyType, keys) ->
                renderVoteKeyTable("$keyType (${keys.size})", keys, users)
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
        .sortedBy { it.second.fold({ "" }, { user -> user.name.lowercase() }) }

    val keySets = sorted.mapNotNull { it.first.set }
    val includePrintLinks = keySets.isNotEmpty()

    details {
        summary(classes = "outline") {
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