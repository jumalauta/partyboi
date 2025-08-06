package party.jml.partyboi.zip

import org.apache.commons.compress.archivers.zip.ZipFile
import party.jml.partyboi.data.catchError
import party.jml.partyboi.system.AppResult
import java.io.File
import java.nio.file.Path

object ZipUtils {
    fun extract(zipFile: File, targetDir: Path): AppResult<Unit> =
        catchError {
            ZipFile.builder()
                .setFile(zipFile)
                .get()
                .use { zip ->
                    zip.entries.iterator().forEach { entry ->
                        val input = zip.getInputStream(entry)
                        val output = targetDir.resolve(entry.name)
                        output.parent.toFile().mkdirs()
                        if (!entry.isDirectory) {
                            input.use { inputStream ->
                                output.toFile().outputStream().use { outputStream ->
                                    inputStream.transferTo(outputStream)
                                }
                            }
                        }
                    }
                }
        }
}