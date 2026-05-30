package party.jml.partyboi.entries

import arrow.core.raise.either
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import kotliquery.Row
import kotliquery.queryOf
import org.apache.commons.compress.archivers.zip.ZipFile
import party.jml.partyboi.AppServices
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.one
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.Label
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.createTemporaryFile
import party.jml.partyboi.system.useTempFile
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import java.nio.file.Path
import java.util.*

class PreviewRepository(val app: AppServices) {
    private val videoExtensions = FileFormatCategory.video.formats().flatMap { it.extensions }.toSet()

    fun scanForScreenshotSource(file: FileDesc): Path? {
        if (file.type == FileDesc.IMAGE) {
            return file.getStorageFile().toPath()
        }
        if (file.type == FileDesc.ZIP_ARCHIVE) {
            return ZipFile.builder()
                .setFile(file.getStorageFile())
                .get()
                .use { zip ->
                    zip.entries.iterator().asSequence()
                        .filter { FileDesc.getType(it.name) == FileDesc.IMAGE }
                        .map { it to heuristicsScore(it.name) }
                        .sortedBy { -it.second }
                        .firstOrNull()
                        ?.let { (entry, _) ->
                            val tempFile = createTemporaryFile().toPath()
                            zip.getInputStream(entry).use { inputStream ->
                                tempFile.toFile().outputStream().use { outputStream ->
                                    inputStream.transferTo(outputStream)
                                }
                            }
                            tempFile
                        }
                }
        }
        return null
    }

    suspend fun store(entryId: UUID, source: Path): AppResult<Unit> = either {
        val inputImage = ImmutableImage.loader().fromPath(source)
        val writer = JpegWriter()
        useTempFile { thumbnailImage ->
            inputImage.scaleToHeight(400).output(writer, thumbnailImage)
            useTempFile { previewImage ->
                inputImage.output(writer, previewImage)
                storeAssets(
                    entryId = entryId,
                    thumbnailFile = thumbnailImage,
                    previewFile = previewImage,
                    thumbnailFilename = "screenshot-${entryId}-thumbnail.jpg",
                    previewFilename = "screenshot-${entryId}.jpg",
                ).bind()
            }
        }
    }

    suspend fun store(entryId: UUID, upload: FileUpload): AppResult<Unit> = either {
        useTempFile { tempFile ->
            upload.write(tempFile).bind()
            store(entryId, tempFile)
        }
    }

    suspend fun get(entryId: UUID): AppResult<Preview> = either {
        val entry = getPreviewEntry(entryId).bind()
        val previewFileDesc = entry.previewFileId?.let { app.files.getById(it).bind() }
        Preview(
            entryId = entryId,
            systemPath = app.files.getStorageFile(entry.fileId).toPath(),
            previewFilePath = entry.previewFileId?.let { app.files.getStorageFile(it).toPath() },
            previewFileIsVideo = previewFileDesc?.extension in videoExtensions,
        )
    }

    suspend fun getEntryPreviews(entries: List<EntryBase>): List<Preview> =
        entries
            .map { app.previews.get(it.id) }
            .flatMap { it.fold({ emptyList() }, { listOf(it) }) }

    suspend fun getFile(entryId: UUID): AppResult<Path> = either {
        val entry = getPreviewEntry(entryId).bind()
        app.files.getStorageFile(entry.fileId).toPath()
    }

    suspend fun getThumbnailFileDesc(entryId: UUID): AppResult<FileDesc> = either {
        val entry = getPreviewEntry(entryId).bind()
        app.files.getById(entry.fileId).bind()
    }

    suspend fun getPreviewFileDesc(entryId: UUID): AppResult<FileDesc> = either {
        val entry = getPreviewEntry(entryId).bind()
        val fileId = entry.previewFileId ?: entry.fileId
        app.files.getById(fileId).bind()
    }

    suspend fun storeAssets(
        entryId: UUID,
        thumbnailFile: Path,
        previewFile: Path,
        thumbnailFilename: String,
        previewFilename: String,
    ): AppResult<Unit> = either {
        val thumbnailDesc = app.files.store(
            tempFile = thumbnailFile.toFile(),
            originalFilename = thumbnailFilename,
            processed = true,
        ).bind()
        val previewDesc = app.files.store(
            tempFile = previewFile.toFile(),
            originalFilename = previewFilename,
            processed = true,
        ).bind()
        savePreviewEntry(
            entryId = entryId,
            fileId = thumbnailDesc.id,
            previewFileId = previewDesc.id,
        ).bind()
    }

    private suspend fun getPreviewEntry(entryId: UUID): AppResult<PreviewEntry> =
        app.db.use {
            one(
                queryOf(
                    "SELECT * FROM preview WHERE entry_id = ?",
                    entryId
                ).map(PreviewEntry.fromRow)
            )
        }

    private suspend fun savePreviewEntry(entryId: UUID, fileId: UUID, previewFileId: UUID?): AppResult<Unit> =
        app.db.use {
            exec(
                queryOf(
                    """
                    INSERT INTO preview (entry_id, file_id, preview_file_id)
                    VALUES (?, ?, ?)
                    ON CONFLICT(entry_id) DO UPDATE SET
                        file_id = EXCLUDED.file_id,
                        preview_file_id = EXCLUDED.preview_file_id
                    """.trimIndent(),
                    entryId,
                    fileId,
                    previewFileId,
                )
            )
        }

    private fun heuristicsScore(filename: String): Int {
        val magicWords = mapOf(
            "final" to 5,
            "screenshot" to 4,
            "preview" to 3,
            "thumbnail" to 3,
        )
        val lcName = filename.lowercase()
        return magicWords.map { (key, value) -> if (lcName.contains(key)) value else 0 }.sum()
    }
}

data class Preview(
    val entryId: UUID,
    val systemPath: Path,
    val previewFilePath: Path? = null,
    val previewFileIsVideo: Boolean = false,
) {
    fun externalUrl() = "/entries/$entryId/preview-thumbnail"
    fun externalPreviewFileUrl() = "/entries/$entryId/preview-file"
}

data class NewPreview(
    @Label("Upload a new preview file:")
    @NotEmpty
    val file: FileUpload
) : Validateable<NewPreview> {
    companion object {
        val Empty = NewPreview(FileUpload.Empty)
    }
}

data class PreviewEntry(
    val entryId: UUID,
    val fileId: UUID,
    val previewFileId: UUID?,
) {
    companion object {
        val fromRow: (Row) -> PreviewEntry = { row ->
            PreviewEntry(
                entryId = row.uuid("entry_id"),
                fileId = row.uuid("file_id"),
                previewFileId = row.uuidOrNull("preview_file_id"),
            )
        }
    }
}