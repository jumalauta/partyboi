package party.jml.partyboi.workqueue

import arrow.core.raise.either
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import party.jml.partyboi.system.AppResult

@Serializable
sealed interface Task {
    suspend fun execute(): AppResult<Unit>
}

@Serializable
data class ProcessAudio(
    val source: String,
    val target: String
) : Task {
    override suspend fun execute(): AppResult<Unit> = either {
        delay(15000)
    }
}