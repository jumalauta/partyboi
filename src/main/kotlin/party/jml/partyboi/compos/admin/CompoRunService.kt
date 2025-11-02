package party.jml.partyboi.compos.admin

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.*
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.screen.slides.Slide
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.TempDir
import party.jml.partyboi.system.createTemporaryFile
import party.jml.partyboi.zip.ZipUtils
import java.io.*
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory


class CompoRunService(app: AppServices) : Service(app) {
    private val hostCache = EitherCache<UUID, AppError, ExtractedEntry>()
    val compoSteps = property<CompoSteps?>("compoSteps", null)

    suspend fun prepareFiles(compoId: UUID, useFoldersForSingleFiles: Boolean): AppResult<TempDir> = either {
        val tempDir = TempDir()
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
                        tempDir.path,
                        useFoldersForSingleFiles,
                        includeOrderNumber = true,
                    )
                if (fileDesc.type == FileDesc.ZIP_ARCHIVE) {
                    ZipUtils.extract(fileDesc.getStorageFile(), targetFilename)
                } else {
                    copyFile(fileDesc, targetFilename)
                }
            }
        }
        tempDir
    }

    fun compressDirectory(sourceDir: Path): AppResult<File> =
        Either.catch {
            val outputFile = createTemporaryFile()
            ZipOutputStream(FileOutputStream(outputFile)).use { zipFile ->
                compressDirectoryToZipfile(sourceDir, sourceDir, zipFile)
            }
            outputFile
        }.mapLeft { InternalServerError(it) }

    suspend fun extractEntryFiles(fileId: UUID): AppResult<ExtractedEntry> = either {
        val file = app.files.getById(fileId).bind()
        val entry = app.entries.getByFileId(fileId).bind()
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
        hostCache.memoize(file.id) {
            val effect = if (file.type == FileDesc.ZIP_ARCHIVE) {
                ZipUtils.extract(file.getStorageFile(), targetFilename)
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

    suspend fun compressAllEntries(): AppResult<File> = either {
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
                app.files.getLatest(entry.id, originalsOnly = true).onRight { file ->
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
        }

        // Compress zip file
        compressDirectory(dir).bind()
    }

    suspend fun initCompoSteps(compo: Compo): AppResult<CompoSteps> = either {
        val currentState = compoSteps.get().bind()

        if (currentState == null || currentState.hasEnded() || currentState.compoId != compo.id) {
            val entries = app.entries.getEntriesForCompo(compo.id).bind()

            val steps = listOf(
                CompoStep.StartsSoon(compo.name, compo.id),
                CompoStep.StartsNow(compo.name, compo.id),
            ) + entries.mapIndexed { idx, entry -> CompoStep.Entry(entry, idx) } +
                    listOf(CompoStep.End(compo.name, compo.id))

            val result = CompoSteps(compo.id, null, steps)
            compoSteps.set(result).bind()

            result
        } else {
            currentState
        }
    }

    suspend fun nextCompoStep() = either {
        compoSteps.get().bind()?.let { steps ->
            compoSteps.set(steps.next(app))
        }
    }

    suspend fun prevCompoStep() = either {
        compoSteps.get().bind()?.let { steps ->
            compoSteps.set(steps.prev(app))
        }
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
}

data class ExtractedEntry(
    val isFolder: Boolean,
    val dir: File
)

@Serializable
data class CompoSteps(
    @Serializable(with = UUIDSerializer::class)
    val compoId: UUID,
    val current: Int?,
    val steps: List<CompoStep>,
) {
    suspend fun next(app: AppServices): CompoSteps = activateStep(current?.let { it + 1 } ?: 0, app)
    suspend fun prev(app: AppServices): CompoSteps {
        current?.let { steps[it].undo(app) }
        return activateStep(current?.let { it - 1 } ?: 0, app)
    }

    suspend fun activateStep(index: Int, app: AppServices): CompoSteps =
        if (index >= 0 && index < steps.size) {
            val step = steps[index]
            step.activate(app)
            app.screen.showInMemorySlide(step.getSlide())
            copy(current = index)
        } else {
            this
        }

    fun hasEnded(): Boolean = current == steps.lastIndex
}

@Serializable
sealed interface CompoStep {
    fun title(): String
    fun icon(): String
    fun getSlide(): Slide<*>
    suspend fun activate(app: AppServices) {}
    suspend fun undo(app: AppServices) {}

    @Serializable
    data class Message(
        val header: String,
        val body: String?
    ) : CompoStep {
        override fun title() = "Message: ${header}"
        override fun icon() = "comment"
        override fun getSlide() = TextSlide(header, body ?: "", "compo")
    }

    @Serializable
    data class StartsSoon(
        val compoName: String,
        @Serializable(with = UUIDSerializer::class)
        val compoId: UUID,
    ) : CompoStep {
        override fun title() = "$compoName compo starts soon"
        override fun icon() = "comment"
        override fun getSlide() = TextSlide.compoStartsSoon(compoName)

        override suspend fun activate(app: AppServices) {
            app.compos.allowSubmit(compoId, false)
            app.compos.allowVoting(compoId, false)
            app.compos.publishResults(compoId, false)
        }
    }

    @Serializable
    data class StartsNow(
        val compoName: String,
        @Serializable(with = UUIDSerializer::class)
        val compoId: UUID,
    ) : CompoStep {
        override fun title() = "$compoName compo starts now"
        override fun icon() = "circle-play"
        override fun getSlide() = TextSlide.compoStartsNow(compoName)

        override suspend fun activate(app: AppServices) {
            app.votes.startLiveVoting(compoId)
        }

        override suspend fun undo(app: AppServices) {
            app.votes.closeLiveVoting()
        }
    }

    @Serializable
    data class End(
        val compoName: String,
        @Serializable(with = UUIDSerializer::class)
        val compoId: UUID,
    ) : CompoStep {
        override fun title() = "$compoName compo has ended!"
        override fun icon() = "circle-stop"
        override fun getSlide() = TextSlide.compoHasEnded(compoName)

        override suspend fun activate(app: AppServices) {
            app.votes.closeLiveVoting()
            app.compos.setVisible(compoId, true)
            app.compos.allowVoting(compoId, true)
        }

        override suspend fun undo(app: AppServices) {
            either {
                app.votes.startLiveVoting(compoId)
                val entries = app.entries.getEntriesForCompo(compoId).bind()
                entries.forEach { entry -> app.votes.addEntryToLiveVoting(entry) }
                app.compos.allowVoting(compoId, false)
            }
        }
    }

    @Serializable
    data class Entry(
        val entry: party.jml.partyboi.entries.Entry,
        val index: Int,
    ) : CompoStep {
        override fun title() = "#$index: ${entry.author} - ${entry.title}"
        override fun icon() = "eye"
        override fun getSlide() = TextSlide.compoSlide(index, entry)

        override suspend fun activate(app: AppServices) {
            app.votes.addEntryToLiveVoting(entry)
        }

        override suspend fun undo(app: AppServices) {
            app.votes.removeEntryFromLiveVoting(entry)
        }
    }
}

