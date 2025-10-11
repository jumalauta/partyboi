package party.jml.partyboi.workqueue

import arrow.core.raise.either
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.system.AppResult
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

@Serializable
sealed interface Task {
    suspend fun execute(app: AppServices): AppResult<Unit>
}

@Serializable
data class NormalizeLoudness(
    val file: FileDesc
) : Task {
    override suspend fun execute(app: AppServices): AppResult<Unit> = either {
        val fileDesc = app.files.getById(file.id).bind()
        val fileId = fileDesc.id

        val inputFile = app.files.getStorageFile(fileId)
        val normalizedFile = app.ffmpeg.normalizeLoudness(inputFile)

        val normalizedFileDesc = app.files.store(
            tempFile = normalizedFile,
            originalFilename = Path(fileDesc.originalFilename).nameWithoutExtension + " (normalized).flac",
            processed = true,
            info = "Loudness normalized to -23 LUFS",
        ).bind()

        app.entries.getByFileId(fileId).onRight { entry ->
            app.entries.associateFile(
                fileId = normalizedFileDesc.id,
                entryId = entry.id
            )
        }
    }
}