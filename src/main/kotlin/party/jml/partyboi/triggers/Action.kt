package party.jml.partyboi.triggers

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError

sealed interface Action {
    fun description(app: AppServices): Either<AppError, String>
    fun apply(app: AppServices): Either<AppError, Unit>
    fun toJson(): String
}

@Serializable
data class OpenCloseVoting(
    val compoId: Int,
    val open: Boolean,
) : Action {
    override fun description(app: AppServices): Either<AppError, String> = either {
        val compo = app.compos.getById(compoId).bind()
        "${if (open) "Open" else "Close"} voting for ${compo.name} compo"
    }
    override fun apply(app: AppServices): Either<AppError, Unit> = app.compos.allowVoting(compoId, open)
    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class OpenCloseSubmitting(
    val compoId: Int,
    val open: Boolean,
) : Action {
    override fun description(app: AppServices): Either<AppError, String> = either {
        val compo = app.compos.getById(compoId).bind()
        "${if (open) "Open" else "Close"} submitting entries for ${compo.name} compo"
    }
    override fun apply(app: AppServices): Either<AppError, Unit> = app.compos.allowSubmit(compoId, open)
    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class OpenLiveVoting(
    val compoId: Int
) : Action {
    override fun description(app: AppServices): Either<AppError, String> = either {
        val compo = app.compos.getById(compoId).bind()
        "Open live voting on ${compo.name} compo"
    }
    override fun apply(app: AppServices): Either<AppError, Unit> = runBlocking {
        app.votes.startLiveVoting(compoId).right()
    }
    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
object CloseLiveVoting : Action {
    override fun description(app: AppServices): Either<AppError, String> = either {
        "Close live voting"
    }
    override fun apply(app: AppServices): Either<AppError, Unit> = runBlocking {
        app.votes.closeLiveVoting().right()
    }
    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class EnableLiveVotingForEntry(
    val entryId: Int
) : Action {
    override fun description(app: AppServices): Either<AppError, String> = either {
        val entry = app.entries.get(entryId).bind()
        "Enable live voting for ${entry.author} – ${entry.title}"
    }
    override fun apply(app: AppServices): Either<AppError, Unit> =
        app.entries.get(entryId).map { entry -> runBlocking { app.votes.addEntryToLiveVoting(entry) } }
    override fun toJson(): String = Json.encodeToString(this)
}
