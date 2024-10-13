package party.jml.partyboi.admin.compos

import arrow.core.Either
import arrow.core.raise.either
import org.apache.commons.compress.archivers.zip.ZipFile
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.data.NotFound
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.FileDesc
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory


class CompoRunService(val app: AppServices) {
    fun prepareFiles(compoId: Int): Either<AppError, Path> = either {
        val tempDir = createTempDirectory()
        val compo = app.compos.getById(compoId).bind()
        val entries = app.entries.getEntriesForCompo(compoId).bind()
            .filter { it.qualified }
            .mapIndexed { index, entry -> entry.copy(runOrder = index + 1) }

        entries.forEach { entry ->
            val fileDesc = getLatestFileDesc(entry).bind()
            val targetFilename = app.files.makeCompoRunFileOrDirName(fileDesc, entry, compo, tempDir)
            if (fileDesc.type == "zip") {
                extractZip(fileDesc, targetFilename)
            } else {
                copyFile(fileDesc, targetFilename)
            }
        }
        tempDir
    }

    fun compressDirectory(sourceDir: Path): Either<AppError, Path> =
        Either.catch {
            val outputFile = kotlin.io.path.createTempFile()
            ZipOutputStream(FileOutputStream(outputFile.toFile())).use { zipFile ->
                compressDirectoryToZipfile(sourceDir, sourceDir, zipFile)
            }
            outputFile
        }.mapLeft { InternalServerError(it.toString()) }

    @Throws(IOException::class, FileNotFoundException::class)
    private fun compressDirectoryToZipfile(rootDir: Path, sourceDir: Path, out: ZipOutputStream) {
        for (file in sourceDir.toFile().listFiles()!!) {
            if (file.isDirectory) {
                compressDirectoryToZipfile(rootDir,sourceDir.resolve(file.name), out)
            } else {
                val sourceFile = sourceDir.resolve(file.name)
                val entryName = rootDir.relativize(sourceFile).toString()
                val entry = ZipEntry(entryName)
                out.putNextEntry(entry)

                val input = FileInputStream(sourceFile.toFile())
                input.transferTo(out)
            }
        }
    }

    private fun getLatestFileDesc(entry: Entry) =
        app.files.getLatest(entry.id)
            .mapLeft { when(it) {
                is NotFound -> NotFound("Entry '${entry.author} - ${entry.title}' does not have file. Disqualify it first.")
                else -> it
            }}

    private fun copyFile(source: FileDesc, target: Path) {
        source.getStorageFile().copyTo(target.toFile())
    }

    private fun extractZip(source: FileDesc, target: Path): Either<Throwable, Unit> =
        Either.catch {
            ZipFile.builder()
                .setFile(source.getStorageFile())
                .get()
                .use { zip ->
                    zip.entries.iterator().forEach { entry ->
                        val input = zip.getInputStream(entry)
                        val output = target.resolve(entry.name)
                        output.parent.toFile().mkdirs()
                        input.use { inputStream ->
                            output.toFile().outputStream().use { outputStream ->
                                inputStream.transferTo(outputStream)
                            }
                        }
                    }
                }
        }
}