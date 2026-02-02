package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.util.cio.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.Service
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.FileChecksums
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.data.toFilenameToken
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.workqueue.NormalizeLoudness
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.pathString

class FileRepository(app: AppServices) : Service(app) {
    private val db = app.db

    init {
        runBlocking {
            migrateFileDirectories()
        }
    }

    suspend fun store(newFile: FileUpload, tx: TransactionalSession? = null): AppResult<FileDesc> =
        store(
            tempFile = newFile.tempFile,
            originalFilename = newFile.name,
            processed = false,
            info = null,
            tx = tx,
        )

    suspend fun store(
        tempFile: File,
        originalFilename: String,
        processed: Boolean = false,
        info: String? = null,
        tx: TransactionalSession? = null
    ): AppResult<FileDesc> =
        either {
            val desc = add(
                FileDesc(
                    id = UUID.randomUUID(),
                    originalFilename = originalFilename,
                    deprecatedStorageFilename = null,
                    type = FileDesc.getType(originalFilename),
                    size = getSize(tempFile).getOrElse { 0 },
                    uploadedAt = Instant.fromEpochSeconds(0),
                    checksum = getChecksum(tempFile).getOrNull(),
                    processed = processed,
                    info = info,
                ), tx
            ).bind()

            val storagePath = app.config.filesDir.resolve(desc.id.toString())
            tempFile.toPath().moveTo(storagePath)

            desc
        }

    suspend fun replaceFile(fileId: UUID, tempFile: File): AppResult<FileDesc> =
        either {
            val desc = getById(fileId).bind() // Ensure that the file id exists
            val storagePath = app.config.filesDir.resolve(fileId.toString())
            tempFile.toPath().moveTo(storagePath, overwrite = true)
            desc
        }

    suspend fun deleteAll() = db.use { it.exec(queryOf("TRUNCATE TABLE file CASCADE")) }

    private fun getSize(absoluteFile: File): Either<InternalServerError, Long> =
        Either.catch { Files.size(absoluteFile.toPath()) }.mapLeft { InternalServerError(it) }

    private fun getChecksum(absoluteFile: File): AppResult<String> =
        FileChecksums.md5sum(absoluteFile)


    private suspend fun migrateFileDirectories() {
        val basePath = app.config.filesDir
        val entriesPath = basePath.resolve(deprecatedEntriesDir).toFile()
        if (entriesPath.exists()) {
            either {
                val entryFiles = db.use {
                    it.many(
                        queryOf("SELECT * FROM file JOIN entry_file ON entry_file.file_id = file.id")
                            .map(FileDesc.fromRow)
                    )
                }.bind()

                entryFiles.forEach { fileDesc ->
                    fileDesc.deprecatedStorageFilename?.let {
                        val storageFile = basePath.resolve(it)
                        if (storageFile.exists()) {
                            val newFile = basePath.resolve(fileDesc.id.toString())
                            storageFile.moveTo(newFile, overwrite = true)
                        }
                    }
                }

                entriesPath.deleteRecursively()
            }
        }
    }

    fun makeCompoRunFileOrDirName(
        fileDesc: FileDesc,
        entry: Entry,
        compo: Compo,
        targetDir: Path,
        useFoldersForSingleFiles: Boolean,
        includeOrderNumber: Boolean,
    ): Path {
        val compoName = compo.name.toFilenameToken(true) ?: "compo-${compo.id}"
        val authorClean = entry.author.toFilenameToken(false)
        val titleClean = entry.title.toFilenameToken(false)
        val extension = if (fileDesc.isArchive) "" else ".${fileDesc.extension}"
        val order = if (includeOrderNumber) {
            entry.runOrder.toString().padStart(2, '0') + "-"
        } else {
            ""
        }

        return if (useFoldersForSingleFiles && !fileDesc.isArchive) {
            Paths.get(
                targetDir.absolutePathString(),
                compoName,
                "$order$authorClean-$titleClean/$order$authorClean-$titleClean$extension"
            )
        } else {
            Paths.get(targetDir.absolutePathString(), compoName, "$order$authorClean-$titleClean$extension")
        }
    }

    fun makeDistributionFileName(
        fileDesc: FileDesc,
        entry: Entry,
        compo: Compo,
        targetDir: Path,
    ): Path {
        val compoName = compo.name.toFilenameToken(true) ?: "compo-${compo.id}"
        val authorClean = entry.author.toFilenameToken(false, 128)
        val titleClean = entry.title.toFilenameToken(false, 128)
        val extension = fileDesc.extension

        return Paths.get(targetDir.absolutePathString(), compoName, "$authorClean-$titleClean.$extension")
    }

    private suspend fun add(file: FileDesc, tx: TransactionalSession? = null): AppResult<FileDesc> = either {
        val result = db.use(tx) {
            it.one(
                queryOf(
                    """
                        INSERT INTO file(orig_filename, storage_filename, type, size, checksum, processed, info) 
                        VALUES(?, ?, ?, ?, ?, ?, ?) 
                        RETURNING *""".trimIndent(),
                    file.originalFilename,
                    "",
                    file.type,
                    file.size,
                    file.checksum,
                    file.processed,
                    file.info,
                ).map(FileDesc.fromRow)
            ).bind()
        }
        postProcessUpload(result)
        result
    }

    suspend fun getById(fileId: UUID): AppResult<FileDesc> = db.use {
        it.one(
            queryOf(
                """
                    SELECT * 
                    FROM file 
                    WHERE id = ?
                    """.trimIndent(),
                fileId
            ).map(FileDesc.fromRow)
        )
    }

    suspend fun getLatest(entryId: UUID, originalsOnly: Boolean): AppResult<FileDesc> = db.use {
        it.one(
            queryOf(
                """                    
                    SELECT *
                    FROM file
                    JOIN entry_file ON entry_file.file_id = file.id
                    WHERE
                        entry_id = ?
                        ${if (originalsOnly) " AND NOT processed" else ""}
                    ORDER BY uploaded_at DESC
                    LIMIT 1""".trimIndent(),
                entryId
            ).map(FileDesc.fromRow)
        )
    }

    suspend fun getAllVersions(entryId: UUID): AppResult<List<FileDesc>> = db.use {
        it.many(
            queryOf(
                """
                    SELECT *
                    FROM file
                    JOIN entry_file ON entry_file.file_id = file.id
                    WHERE entry_id = ?
                    ORDER BY uploaded_at DESC
                """.trimIndent(),
                entryId
            ).map(FileDesc.fromRow)
        )
    }

    fun getStorageFile(fileId: UUID): File =
        app.config.filesDir.resolve(fileId.toString()).toFile()

    suspend fun postProcessUpload(file: FileDesc) {
        if (!file.processed) {
            val streamingAudioFormats = FileFormatCategory.streamingAudio.formats().flatMap { it.extensions }
            if (streamingAudioFormats.contains(file.extension)) {
                app.workQueue.addTask(NormalizeLoudness(file))
            }
        }
    }

    suspend fun getEntryIdsWithFiles(includeProcessedFiles: Boolean): AppResult<List<EntryFileAssociation>> = db.use {
        it.many(
            queryOf(
                """
                    WITH entry_file_with_rn AS (
                        SELECT
                            entry_id,
                            id,
                            row_number() OVER (PARTITION BY entry_id ORDER BY uploaded_at DESC) rn
                        FROM entry_file
                        JOIN file ON file.id = entry_file.file_id
                        ${if (includeProcessedFiles) "" else "WHERE NOT processed"}
                    )
                    SELECT
                        entry_id,
                        id file_id
                    FROM entry_file_with_rn
                    WHERE rn = 1
        """.trimIndent()
            ).map(EntryFileAssociation.fromRow)
        )
    }

    suspend fun getAll() = db.use {
        it.many(queryOf("SELECT * FROM file").map(FileDesc.fromRow))
    }

    companion object {
        val deprecatedEntriesDir: Path = Path.of("entries")
    }
}

data class EntryFileAssociation(
    val entryId: UUID,
    val fileId: UUID,
) {
    companion object {
        val fromRow: (Row) -> EntryFileAssociation = { row ->
            EntryFileAssociation(
                entryId = row.uuid("entry_id"),
                fileId = row.uuid("file_id"),
            )
        }
    }
}

@Serializable
data class FileDesc(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val originalFilename: String,
    @Serializable(with = PathSerializer::class)
    val deprecatedStorageFilename: Path?,
    val type: String,
    val size: Long,
    val uploadedAt: Instant,
    val checksum: String?,
    val processed: Boolean,
    val info: String?
) {
    val isArchive = type == ZIP_ARCHIVE
    val extension by lazy { File(originalFilename).extension.lowercase() }

    fun getStorageFile(): File = Config.get().filesDir.resolve(id.toString()).toFile()
    fun getChannelProvider(): ChannelProvider = ChannelProvider {
        getStorageFile().readChannel()
    }

    companion object {
        val fromRow: (Row) -> FileDesc = { row ->
            FileDesc(
                id = row.uuid("id"),
                originalFilename = row.string("orig_filename"),
                deprecatedStorageFilename = row.stringOrNull("storage_filename")?.let { Paths.get(it) },
                type = row.string("type"),
                size = row.long("size"),
                uploadedAt = row.instant("uploaded_at").toKotlinInstant(),
                checksum = row.stringOrNull("checksum"),
                processed = row.boolean("processed"),
                info = row.stringOrNull("info"),
            )
        }

        fun getType(filename: String): String =
            when (val extension = File(filename).extension.lowercase()) {
                "png", "jpg", "jpeg", "gif", "webp" -> IMAGE
                "mp3", "ogg", "flac", "wav" -> SOUND
                "zip" -> ZIP_ARCHIVE
                else -> extension
            }

        const val IMAGE = ":image"
        const val SOUND = ":sound"
        const val ZIP_ARCHIVE = ":zip"
    }
}

enum class FileFormatCategory(val description: String) {
    archive("Archive"),
    image("Image"),
    streamingAudio("Streaming audio"),
    tracker("Tracker module"),
    midi("MIDI"),
    video("Video"),
    ;

    fun formats(): List<FileFormat> = FileFormat.entries.filter { it.category == this }
}

enum class FileFormat(
    val description: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
    val category: FileFormatCategory,
) {
    // Archive formats
    zip(
        description = "ZIP archive",
        extensions = listOf("zip"),
        mimeTypes = listOf("application/zip", "application/x-zip-compressed"),
        category = FileFormatCategory.archive
    ),

    // Image formats
    jpeg(
        description = "JPEG image",
        extensions = listOf("jpg", "jpeg"),
        mimeTypes = listOf("image/jpeg"),
        category = FileFormatCategory.image
    ),
    gif(
        description = "GIF image (Graphics Interchange Format)",
        extensions = listOf("gif"),
        mimeTypes = listOf("image/gif"),
        category = FileFormatCategory.image
    ),
    png(
        description = "PNG image (Portable Network Graphics)",
        extensions = listOf("png"),
        mimeTypes = listOf("image/png"),
        category = FileFormatCategory.image
    ),
    svg(
        description = "Scalable Vector Graphics (SVG)",
        extensions = listOf("svg"),
        mimeTypes = listOf("image/svg+xml"),
        category = FileFormatCategory.image
    ),
    tiff(
        description = "TIFF image (Tagged Image File Format)",
        extensions = listOf("tif", "tiff"),
        mimeTypes = listOf("image/tiff"),
        category = FileFormatCategory.image
    ),
    webp(
        description = "WEBP image",
        extensions = listOf("webp"),
        mimeTypes = listOf("image/webp"),
        category = FileFormatCategory.image
    ),

    // Audio formats
    aac(
        description = "AAC (Advanced Audio Coding)",
        extensions = listOf("aac"),
        mimeTypes = listOf("audio/aac"),
        category = FileFormatCategory.streamingAudio
    ),
    aiff(
        description = "AIFF (Audio Interchange File Format)",
        extensions = listOf("aiff", "aif", "ief"),
        mimeTypes = listOf(
            "audio/rmf",
            "audio/aiff",
            "sound/aiff",
            "audio/x-aiff"
        ),
        category = FileFormatCategory.streamingAudio
    ),
    flac(
        description = "FLAC",
        extensions = listOf("flac"),
        mimeTypes = listOf("audio/flac", "audio/x-flac"),
        category = FileFormatCategory.streamingAudio,
    ),
    mp3(
        description = "MP3",
        extensions = listOf("mp3"),
        mimeTypes = listOf("audio/mpeg"),
        category = FileFormatCategory.streamingAudio
    ),
    ogg(
        description = "Ogg Vorbis",
        extensions = listOf("ogg", "oga"),
        mimeTypes = listOf("audio/ogg"),
        category = FileFormatCategory.streamingAudio
    ),
    opus(
        description = "Opus audio in Ogg container",
        extensions = listOf("opus"),
        mimeTypes = listOf("audio/ogg"),
        category = FileFormatCategory.streamingAudio
    ),
    wav(
        description = "Waveform Audio Format",
        extensions = listOf("wav"),
        mimeTypes = listOf("audio/wav"),
        category = FileFormatCategory.streamingAudio
    ),
    weba(
        description = "WEBM audio",
        extensions = listOf("weba"),
        mimeTypes = listOf("audio/webm"),
        category = FileFormatCategory.streamingAudio
    ),

    // Music formats
    mod(
        description = "ProTracker MOD",
        extensions = listOf("mod"),
        mimeTypes = emptyList(),
        category = FileFormatCategory.tracker
    ),
    s3m(
        description = "Scream Tracker 3",
        extensions = listOf("s3m"),
        mimeTypes = emptyList(),
        category = FileFormatCategory.tracker
    ),
    xm(
        description = "FastTracker 2",
        extensions = listOf("xm"),
        mimeTypes = emptyList(),
        category = FileFormatCategory.tracker
    ),
    it(
        description = "Impulse Tracker",
        extensions = listOf("it"),
        mimeTypes = emptyList(),
        category = FileFormatCategory.tracker
    ),
    openMpt(
        description = "OpenMPT",
        extensions = listOf("mptm"),
        mimeTypes = emptyList(),
        category = FileFormatCategory.tracker
    ),
    midi(
        description = "Musical Instrument Digital Interface (MIDI)",
        extensions = listOf("mid", "midi"),
        mimeTypes = listOf("audio/midi", "audio/x-midi"),
        category = FileFormatCategory.midi
    ),

    // Video formats
    mp4(
        description = "MP4 video",
        extensions = listOf("mp4"),
        mimeTypes = listOf("video/mp4"),
        category = FileFormatCategory.video
    ),
    mpeg(
        description = "MPEG Video",
        extensions = listOf("mpeg"),
        mimeTypes = listOf("video/mpeg"),
        category = FileFormatCategory.video
    ),
    ogv(
        description = "Ogg video",
        extensions = listOf("ogv"),
        mimeTypes = listOf("video/ogg"),
        category = FileFormatCategory.video
    ),
    webm(
        description = "WEBM video",
        extensions = listOf("webm"),
        mimeTypes = listOf("video/webm"),
        category = FileFormatCategory.video
    )
}

object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Path = Paths.get(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.pathString)
    }

}