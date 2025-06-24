package party.jml.partyboi.telnet

import arrow.core.getOrElse
import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.User
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.entries.VotableEntry

class SocketVotingCompoMenu : AuthorizedSocketPage, SocketMenu {
    override fun getTitle(): String = "Voting"

    override suspend fun input(
        query: String,
        state: SocketState,
        app: AppServices
    ): SocketState {
        val compo = getCompoList(app)[formattedQuery(query)]
        return if (compo == null) {
            state
        } else {
            state.goto(SocketVotingEntryMenu(compo))
        }
    }

    override suspend fun getItems(state: SocketState, app: AppServices): Map<String, String> =
        getCompoList(app).mapValues { (_, compo) -> compo.name }

    private suspend fun getCompoList(app: AppServices): Map<String, Compo> =
        either {
            app.compos
                .getAllCompos()
                .bind()
                .filter { it.allowVote }
                .mapIndexed { index, compo -> "${index + 1}" to compo }
                .toMap()
        }.getOrElse { emptyMap() }
}

class SocketVotingRegisterVotekey(val err: String?) : AuthorizedSocketPage {
    override fun getTitle(): String = "Register votekey"

    override suspend fun print(
        state: SocketState,
        app: AppServices
    ): String = listOfNotNull(
        err?.let { "$err\n\n" },
        "Enter your votekey (you probably got it with your badge):"
    ).joinToString("\n")

    override suspend fun input(
        query: String,
        state: SocketState,
        app: AppServices
    ): SocketState? =
        app.voteKeys.registerTicket(state.user!!.id, query).fold(
            { state.goto(SocketVotingRegisterVotekey(it.message)) },
            { state.goto(SocketVotingCompoMenu()) }
        )
}

data class SocketVotingEntryMenu(val compo: Compo) : SocketMenu, AuthorizedSocketPage {
    override suspend fun getItems(state: SocketState, app: AppServices): Map<String, String> {
        val entries = getEntries(state.user!!, app)
            .mapValues { "${it.value.author} - ${it.value.title}" to it.value.points }
        val maxLength = entries.map { it.value.first.length }.max()
        return entries.mapValues {
            it.value.first.padEnd(maxLength) + "   " + it.value.second.fold(
                { "(no vote)" },
                { "*".repeat(it) })
        } + mapOf("X" to "Back")
    }

    override fun getTitle(): String = "${compo.name} compo: Select entry to vote"

    override suspend fun input(
        query: String,
        state: SocketState,
        app: AppServices
    ): SocketState? =
        if (formattedQuery(query) == "X") {
            state.goto(SocketVotingCompoMenu())
        } else {
            val entry = getEntries(state.user!!, app)[query]
            if (entry == null) {
                state
            } else {
                state.goto(SocketVotingCastVote(compo, entry))
            }
        }

    private suspend fun getEntries(user: User, app: AppServices): Map<String, VotableEntry> =
        either {
            app.entries
                .getVotableEntries(user.id)
                .bind()
                .filter { it.compoId == compo.id }
                .mapIndexed { index, entry -> "${index + 1}" to entry }
                .toMap()
        }.getOrElse { emptyMap() }

}

data class SocketVotingCastVote(val compo: Compo, val entry: VotableEntry) : SocketMenu, AuthorizedSocketPage {
    override suspend fun getItems(
        state: SocketState,
        app: AppServices
    ): Map<String, String> = mapOf(
        "1" to "*",
        "2" to "**",
        "3" to "***",
        "4" to "****",
        "5" to "*****",
        "X" to "Go back"
    )

    override fun getTitle(): String = "Vote ${entry.author} - ${entry.title}"

    override suspend fun input(
        query: String,
        state: SocketState,
        app: AppServices
    ): SocketState? =
        if (formattedQuery(query) == "X") {
            state.goto(SocketVotingEntryMenu(compo))
        } else {
            app.votes.castVote(state.user!!, entry.id, query.toInt()).fold(
                { state },
                { state.goto(SocketVotingEntryMenu(compo)) }
            )
        }

}