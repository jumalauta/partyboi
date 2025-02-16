package party.jml.partyboi.entries

import arrow.core.*
import arrow.core.raise.either
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import org.apache.commons.compress.archivers.zip.ZipFile
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.FileChecksums
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FileUpload
import java.nio.file.Path
import kotlin.io.path.exists

class ScreenshotRepository(val app: AppServices) {
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
                            val tempFile = kotlin.io.path.createTempFile()
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

    fun store(entryId: Int, source: Path) {
        val inputImage = ImmutableImage.loader().fromPath(source)
        val outputImage = getFile(entryId)
        outputImage.parent.toFile().mkdirs()
        val writer = JpegWriter()
        inputImage.scaleToHeight(400).output(writer, outputImage)
    }

    fun store(entryId: Int, upload: FileUpload): Either<AppError, Unit> = either {
        val tempFile = kotlin.io.path.createTempFile()
        upload.write(tempFile).bind()
        store(entryId, tempFile)
    }

    fun get(entryId: Int): Option<Screenshot> {
        val path = getFile(entryId)
        return if (path.exists()) Some(Screenshot(entryId, path)) else None
    }

    fun getForEntries(entries: List<EntryBase>): List<Screenshot> =
        entries
            .map { app.screenshots.get(it.id) }
            .flatMap { it.fold({ emptyList() }, { listOf(it) }) }

    fun getFile(entryId: Int): Path =
        app.config.screenshotsDir.resolve("screenshot-$entryId.jpg")

    fun getChecksum(entryId: Int): Either<AppError, String> =
        FileChecksums.get(getFile(entryId))

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
    val entryId: Int,
    val systemPath: Path,
) {
    fun externalUrl() = "/entries/$entryId/screenshot.jpg"
}

data class NewScreenshot(
    @property:Field(label = "Upload file")
    val file: FileUpload
) : Validateable<NewScreenshot> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("file", file.name)
    )

    companion object {
        val Empty = NewScreenshot(FileUpload.Empty)
    }
}