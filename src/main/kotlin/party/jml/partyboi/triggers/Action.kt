package party.jml.partyboi.triggers

import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.system.AppResult
import java.util.*

sealed interface Action {
    suspend fun description(app: AppServices): AppResult<String>
    suspend fun apply(app: AppServices): AppResult<Unit>
    fun toJson(): String
}

@Serializable
data class OpenCloseVoting(
    @Serializable(with = UUIDSerializer::class)
    val compoId: UUID,
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
    @Serializable(with = UUIDSerializer::class)
    val compoId: UUID,
    val open: Boolean,
) : Action {
    override suspend fun description(app: AppServices): AppResult<String> = either {
        val compo = app.compos.getById(compoId).bind()
        "${if (open) "Open" else "Close"} submitting entries to ${compo.name} compo"
    }

    override suspend fun apply(app: AppServices): AppResult<Unit> = app.compos.allowSubmit(compoId, open)
    override fun toJson(): String = Json.encodeToString(this)
}
