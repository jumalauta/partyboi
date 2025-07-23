package party.jml.partyboi.compos.admin

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import org.apache.commons.compress.archivers.zip.ZipFile
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.*
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.system.AppResult
import java.io.*
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory


class CompoRunService(val app: AppServices) : Logging() {
    private val hostCache = EitherCache<Pair<Int, Int>, AppError, ExtractedEntry>()

    suspend fun prepareFiles(compoId: Int, useFoldersForSingleFiles: Boolean): AppResult<Path> = either {
        val tempDir = createTempDirectory()
        val compo = app.compos.getById(compoId).bind()
        val entries = app.entries.getEntriesForCompo(compoId).bind()
            .filter { it.qualified }
            .mapIndexed { index, entry -> entry.copy(runOrder = index + 1) }

        entries.forEach { entry ->
            getLatestFileDesc(entry).map { fileDesc ->
                val targetFilename =
                    app.files.makeCompoRunFileOrDirName(
                        fileDesc,
                        entry,
                        compo,
                        tempDir,
                        useFoldersForSingleFiles,
                        includeOrderNumber = true,
                    )
                if (fileDesc.type == FileDesc.ZIP_ARCHIVE) {
                    extractZip(fileDesc, targetFilename)
                } else {
                    copyFile(fileDesc, targetFilename)
                }
            }
        }
        tempDir
    }

    fun compressDirectory(sourceDir: Path): AppResult<Path> =
        Either.catch {
            val outputFile = kotlin.io.path.createTempFile()
            ZipOutputStream(FileOutputStream(outputFile.toFile())).use { zipFile ->
                compressDirectoryToZipfile(sourceDir, sourceDir, zipFile)
            }
            outputFile
        }.mapLeft { InternalServerError(it) }

    suspend fun extractEntryFiles(entryId: Int, version: Int): AppResult<ExtractedEntry> = either {
        val file = app.files.getVersion(entryId, version).bind()
        val entry = app.entries.get(entryId).bind()
        val compo = app.compos.getById(entry.compoId).bind()
        val tempDir = createTempDirectory()
        Pair(
            file,
            app.files.makeCompoRunFileOrDirName(
                file,
                entry,
                compo,
                tempDir,
                useFoldersForSingleFiles = true,
                includeOrderNumber = true,
            )
        )
    }.flatMap { target ->
        val (file, targetFilename) = target
        hostCache.memoize(Pair(entryId, file.version)) {
            val effect = if (file.type == FileDesc.ZIP_ARCHIVE) {
                extractZip(file, targetFilename)
            } else {
                copyFile(file, targetFilename)
            }
            effect.map {
                ExtractedEntry(
                    file.type == FileDesc.ZIP_ARCHIVE,
                    targetFilename.toFile()
                )
            }
        }
    }

    suspend fun compressAllEntries(): AppResult<Path> = either {
        val dir = createTempDirectory()

        // Write results
        val results = app.votes.getResultsFileContent(includeInfo = false).bind()
        catchError {
            dir.resolve("results.txt").toFile().writeText(results)
        }.bind()

        val resultsWithInfo = app.votes.getResultsFileContent(includeInfo = true).bind()
        if (resultsWithInfo != results) {
            catchError {
                dir.resolve("results-with-info.txt").toFile().writeText(resultsWithInfo)
            }.bind()
        }

        // Copy latest file version of each qualified entry
        val entries = app.entries.getAllEntries().bind().filter { it.qualified }
        val compos = app.compos.getAllCompos().bind()
        entries.forEach { entry ->
            val compo = compos.find { it.id == entry.compoId }
            if (compo != null) {
                val file = app.files.getLatest(entry.id, originalsOnly = true).bind()
                val target =
                    app.files.makeDistributionFileName(
                        file,
                        entry,
                        compo,
                        dir,
                    )
                copyFile(file, target).bind()
            }
        }

        // Compress zip file
        compressDirectory(dir).bind()
    }

    @Throws(IOException::class, FileNotFoundException::class)
    private fun compressDirectoryToZipfile(rootDir: Path, sourceDir: Path, out: ZipOutputStream) {
        for (file in sourceDir.toFile().listFiles()!!) {
            if (file.isDirectory) {
                compressDirectoryToZipfile(rootDir, sourceDir.resolve(file.name), out)
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

    private suspend fun getLatestFileDesc(entry: Entry) =
        app.files.getLatest(entry.id, originalsOnly = false)
            .mapLeft {
                when (it) {
                    is NotFound -> NotFound("Entry '${entry.author} - ${entry.title}' does not have file. Disqualify it first.")
                    else -> it
                }
            }

    private fun copyFile(source: FileDesc, target: Path) = catchError {
        log.info("Copy $source to $target")
        source.getStorageFile().copyTo(target.toFile())
    }

    private fun extractZip(source: FileDesc, target: Path): AppResult<Unit> =
        catchError {
            ZipFile.builder()
                .setFile(source.getStorageFile())
                .get()
                .use { zip ->
                    zip.entries.iterator().forEach { entry ->
                        val input = zip.getInputStream(entry)
                        val output = target.resolve(entry.name)
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

data class ExtractedEntry(
    val isFolder: Boolean,
    val dir: File
)