package party.jml.partyboi.workqueue

import arrow.core.raise.either
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.entries.NewFileDesc
import party.jml.partyboi.system.AppResult

@Serializable
sealed interface Task {
    suspend fun execute(app: AppServices): AppResult<Unit>
}

@Serializable
data class NormalizeLoudness(
    val file: FileDesc
) : Task {
    override suspend fun execute(app: AppServices): AppResult<Unit> = either {
        val fileDesc = app.files.getVersion(file.entryId, file.version).bind()
        val entry = app.entries.get(fileDesc.entryId).bind()

        val input = app.files.getStorageFile(fileDesc.storageFilename)
        val output = app.files.makeStorageFilename(
            entry = entry,
            originalFilename = fileDesc.originalFilename,
        ).bind()

        app.ffmpeg.normalizeLoudness(input, output.toFile())

        app.files.add(
            NewFileDesc(
                entryId = entry.id,
                originalFilename = fileDesc.originalFilename,
                storageFilename = output,
                processed = true
            )
        ).bind()
    }
}