package party.jml.partyboi.triggers

import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.system.AppResult

sealed interface Action {
    suspend fun description(app: AppServices): AppResult<String>
    suspend fun apply(app: AppServices): AppResult<Unit>
    fun toJson(): String
}

@Serializable
data class OpenCloseVoting(
    val compoId: Int,
    val open: Boolean,
) : Action {
    override suspend fun description(app: AppServices): AppResult<String> = either {
        val compo = app.compos.getById(compoId).bind()
        "${if (open) "Open" else "Close"} voting in ${compo.name} compo"
    }

    override suspend fun apply(app: AppServices): AppResult<Unit> = app.compos.allowVoting(compoId, open)
    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class OpenCloseSubmitting(
    val compoId: Int,
    val open: Boolean,
) : Action {
    override suspend fun description(app: AppServices): AppResult<String> = either {
        val compo = app.compos.getById(compoId).bind()
        "${if (open) "Open" else "Close"} submitting entries to ${compo.name} compo"
    }

    override suspend fun apply(app: AppServices): AppResult<Unit> = app.compos.allowSubmit(compoId, open)
    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class OpenLiveVoting(
    val compoId: Int
) : Action {
    override suspend fun description(app: AppServices): AppResult<String> = either {
        val compo = app.compos.getById(compoId).bind()
        "Open live voting on ${compo.name} compo"
    }

    override suspend fun apply(app: AppServices): AppResult<Unit> = runBlocking {
        app.votes.startLiveVoting(compoId).right()
    }

    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
object CloseLiveVoting : Action {
    override suspend fun description(app: AppServices): AppResult<String> = either {
        "Close live voting"
    }

    override suspend fun apply(app: AppServices): AppResult<Unit> = runBlocking {
        app.votes.closeLiveVoting().right()
    }

    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class EnableLiveVotingForEntry(
    val entryId: Int
) : Action {
    override suspend fun description(app: AppServices): AppResult<String> = either {
        val entry = app.entries.get(entryId).bind()
        "Enable live voting for ${entry.author} – ${entry.title}"
    }

    override suspend fun apply(app: AppServices): AppResult<Unit> =
        app.entries.get(entryId).map { entry -> runBlocking { app.votes.addEntryToLiveVoting(entry) } }

    override fun toJson(): String = Json.encodeToString(this)
}
