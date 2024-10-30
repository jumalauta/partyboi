package party.jml.partyboi.triggers

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError

sealed interface Trigger {
    fun description(app: AppServices): Either<AppError, String>
    fun apply(app: AppServices): Either<AppError, Unit>
    fun toJson(): String
}

@Serializable
data class CompoVotingTrigger(
    val compoId: Int,
    val open: Boolean,
) : Trigger {
    override fun description(app: AppServices): Either<AppError, String> = either {
        val compo = app.compos.getById(compoId).bind()
        "${if (open) "Open" else "Close"} voting for ${compo.name} compo"
    }
    override fun apply(app: AppServices): Either<AppError, Unit> = app.compos.allowVoting(compoId, open)
    override fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class EntrySubmittingTrigger(
    val compoId: Int,
    val open: Boolean,
) : Trigger {
    override fun description(app: AppServices): Either<AppError, String> = either {
        val compo = app.compos.getById(compoId).bind()
        "${if (open) "Open" else "Close"} submitting entries for ${compo.name} compo"
    }
    override fun apply(app: AppServices): Either<AppError, Unit> = app.compos.allowSubmit(compoId, open)
    override fun toJson(): String = Json.encodeToString(this)
}
