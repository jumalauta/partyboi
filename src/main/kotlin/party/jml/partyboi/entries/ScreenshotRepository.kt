package party.jml.partyboi.entries

import arrow.core.*
import arrow.core.raise.either
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import org.apache.commons.compress.archivers.zip.ZipFile
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.Label
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.createTemporaryFile
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

class ScreenshotRepository(val app: AppServices) {
    init {
        app.config.screenshotsDir.toFile().mkdirs()
    }

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

    fun store(entryId: UUID, source: Path) {
        val inputImage = ImmutableImage.loader().fromPath(source)
        val outputImage = getFile(entryId)
        outputImage.parent.toFile().mkdirs()
        val writer = JpegWriter()
        inputImage.scaleToHeight(400).output(writer, outputImage)
    }

    suspend fun store(entryId: UUID, upload: FileUpload): AppResult<Unit> = either {
        val tempFile = createTemporaryFile()
        upload.write(tempFile).bind()
        store(entryId, tempFile.toPath())
    }

    fun get(entryId: UUID): Option<Screenshot> {
        val path = getFile(entryId)
        return if (path.exists()) Some(Screenshot(entryId, path)) else None
    }

    fun getForEntries(entries: List<EntryBase>): List<Screenshot> =
        entries
            .map { app.screenshots.get(it.id) }
            .flatMap { it.fold({ emptyList() }, { listOf(it) }) }

    fun getFile(entryId: UUID): Path =
        app.config.screenshotsDir.resolve("screenshot-$entryId.jpg")

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

data class Screenshot(
    val entryId: UUID,
    val systemPath: Path,
) {
    fun externalUrl() = "/entries/$entryId/screenshot.jpg"
}

data class NewScreenshot(
    @Label("Upload file")
    @NotEmpty
    val file: FileUpload
) : Validateable<NewScreenshot> {
    companion object {
        val Empty = NewScreenshot(FileUpload.Empty)
    }
}