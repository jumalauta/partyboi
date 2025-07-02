package party.jml.partyboi.workqueue

import arrow.core.raise.either
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.entries.NewFileDesc
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
        val fileDesc = app.files.getVersion(file.entryId, file.version).bind()
        val entry = app.entries.get(fileDesc.entryId).bind()

        val newName = Path(fileDesc.originalFilename).nameWithoutExtension + " (normalized).flac"
        val output = app.files.makeStorageFilename(
            entry = entry,
            originalFilename = newName,
        ).bind()

        val inputFile = app.files.getStorageFile(fileDesc.storageFilename)
        val outputFile = app.files.getStorageFile(output)

        app.ffmpeg.normalizeLoudness(inputFile, outputFile)

        app.files.add(
            NewFileDesc(
                entryId = entry.id,
                originalFilename = newName,
                storageFilename = output,
                processed = true,
                info = "Loudness normalized to -23 LUFS"
            )
        ).bind()
    }
}