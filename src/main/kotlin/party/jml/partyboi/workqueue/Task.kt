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
            info = "Loudness normalized to -14 LUFS",
        ).bind()

        app.entries.getByFileId(fileId).onRight { entry ->
            app.entries.associateFile(
                fileId = normalizedFileDesc.id,
                entryId = entry.id
            )
        }
    }
}

@Serializable
data class GeneratePreviewForVideo(
    val file: FileDesc
) : Task {
    override suspend fun execute(app: AppServices): AppResult<Unit> = either {
        val fileDesc = app.files.getById(file.id).bind()
        val entry = app.entries.getByFileId(fileDesc.id).bind()
        val inputFile = app.files.getStorageFile(fileDesc.id)

        val (thumb, clip) = app.ffmpeg.generateVideoPreview(inputFile)

        app.previews.storeAssets(
            entryId = entry.id,
            thumbnailFile = thumb.toPath(),
            previewFile = clip.toPath(),
            thumbnailFilename = "thumb-${entry.id}.webp",
            previewFilename = "preview-${entry.id}.webm",
        ).bind()
    }
}

@Serializable
data class GeneratePreviewForAudio(
    val file: FileDesc
) : Task {
    override suspend fun execute(app: AppServices): AppResult<Unit> = either {
        val fileDesc = app.files.getById(file.id).bind()
        val entry = app.entries.getByFileId(fileDesc.id).bind()
        val inputFile = app.files.getStorageFile(fileDesc.id)

        val (waveformThumb, waveformFull, clip) = app.ffmpeg.generateAudioPreview(inputFile)

        app.previews.storeAudioPreview(
            entryId = entry.id,
            audioFile = clip.toPath(),
            audioFilename = "preview-${entry.id}.webm",
            waveformThumbnailFile = waveformThumb.toPath(),
            waveformPreviewFile = waveformFull.toPath(),
            waveformThumbnailFilename = "waveform-${entry.id}-thumbnail.png",
            waveformPreviewFilename = "waveform-${entry.id}.png",
        ).bind()
    }
}

@Serializable
data class ExtractDuration(
    val file: FileDesc
) : Task {
    override suspend fun execute(app: AppServices): AppResult<Unit> = either {
        val fileDesc = app.files.getById(file.id).bind()
        val entry = app.entries.getByFileId(fileDesc.id).bind()
        val inputFile = app.files.getStorageFile(fileDesc.id)
        val duration = app.ffmpeg.probeDuration(inputFile)
        app.entries.setDuration(entry.id, duration).bind()
    }
}