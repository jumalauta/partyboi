package party.jml.partyboi.entries

import arrow.core.*
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import org.apache.commons.compress.archivers.zip.ZipFile
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import java.nio.file.Path
import kotlin.io.path.exists

class ScreenshotRepository(app: AppServices) {
    fun scanForScreenshotSource(file: FileDesc): Option<Path> {
        if (file.type == "image") {
            return file.getStorageFile().toPath().some()
        }
        if (file.type == "zip") {
            return ZipFile.builder()
                .setFile(file.getStorageFile())
                .get()
                .use { zip ->
                    zip.entries.iterator().asSequence()
                        .filter { NewFileDesc.getType(it.name) == "image" }
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

    fun get(entryId: Int): Option<Path> {
        val path = getFile(entryId)
        return if (path.exists()) Some(path) else None
    }

    private fun getFile(entryId: Int): Path =
        Config.getScreenshotsDir().resolve("screenshot-$entryId.jpg")

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