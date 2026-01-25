package party.jml.partyboi.entries

import arrow.core.Option
import arrow.core.none
import arrow.core.raise.either
import arrow.core.some
import arrow.core.toOption
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
    fun scanForScreenshotSource(file: FileDesc): Option<Path> {
        if (file.type == FileDesc.IMAGE) {
            return file.getStorageFile().toPath().some()
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
                        .toOption()
                        .map { (entry, _) ->
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
        return none()
    }

    suspend fun store(entryId: UUID, source: Path): AppResult<Unit> = either {
        val inputImage = ImmutableImage.loader().fromPath(source)
        useTempFile { outputImage ->
            val writer = JpegWriter()
            inputImage.scaleToHeight(400).output(writer, outputImage)
            saveScreenshot(entryId, outputImage).bind()
        }
    }

    suspend fun store(entryId: UUID, upload: FileUpload): AppResult<Unit> = either {
        useTempFile { tempFile ->
            upload.write(tempFile).bind()
            store(entryId, tempFile)
        }
    }

    suspend fun get(entryId: UUID): AppResult<Preview> =
        getFile(entryId).map { Preview(entryId, it) }

    suspend fun getEntryPreviews(entries: List<EntryBase>): List<Preview> =
        entries
            .map { app.previews.get(it.id) }
            .flatMap { it.fold({ emptyList() }, { listOf(it) }) }

    suspend fun getFile(entryId: UUID): AppResult<Path> = either {
        val entry = getPreviewEntry(entryId).bind()
        app.files.getStorageFile(entry.fileId).toPath()
    }

    private suspend fun saveScreenshot(entryId: UUID, file: Path): AppResult<Unit> = either {
        val fileDesc = app.files.store(
            tempFile = file.toFile(),
            originalFilename = "screenshot-${entryId}.jpg",
            processed = true
        ).bind()
        savePreviewEntry(
            entryId = entryId,
            fileId = fileDesc.id,
        ).bind()
    }

    private suspend fun getPreviewEntry(entryId: UUID): AppResult<PreviewEntry> =
        app.db.use {
            it.one(
                queryOf(
                    "SELECT * FROM preview WHERE entry_id = ?",
                    entryId
                ).map(PreviewEntry.fromRow)
            )
        }

    private suspend fun savePreviewEntry(entryId: UUID, fileId: UUID): AppResult<Unit> =
        app.db.use {
            it.exec(
                queryOf(
                    """
                    INSERT INTO preview (entry_id, file_id)
                    VALUES (?, ?)
                    ON CONFLICT(entry_id) DO UPDATE SET file_id = EXCLUDED.file_id
                    """.trimIndent(),
                    entryId,
                    fileId
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
) {
    fun externalUrl() = "/entries/$entryId/preview.jpg"
}

data class NewPreview(
    @Label("Upload file")
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
) {
    companion object {
        val fromRow: (Row) -> PreviewEntry = { row ->
            PreviewEntry(
                entryId = row.uuid("entry_id"),
                fileId = row.uuid("file_id"),
            )
        }
    }
}