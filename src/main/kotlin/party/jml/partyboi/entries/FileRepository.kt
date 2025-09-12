package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
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
import party.jml.partyboi.db.DbBasicMappers.asIntOrNull
import party.jml.partyboi.db.many
import party.jml.partyboi.db.one
import party.jml.partyboi.db.option
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.workqueue.NormalizeLoudness
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

class FileRepository(app: AppServices) : Service(app) {
    private val db = app.db

    init {
        app.config.entryDir.toFile().mkdirs()
    }

    suspend fun makeStorageFilename(
        entry: Entry,
        originalFilename: String,
        tx: TransactionalSession? = null
    ): AppResult<Path> = either {
        val version = nextVersion(entry.id, tx).bind()
        val compo = app.compos.getById(entry.compoId, tx).bind()
        buildStorageFilename(
            compo.name,
            entry.id,
            version,
            entry.author,
            entry.title,
            originalFilename
        )
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

    suspend fun latestVersion(
        entryId: UUID,
        originalsOnly: Boolean,
        tx: TransactionalSession? = null
    ): AppResult<Option<Int>> =
        db.use(tx) {
            it.option(
                queryOf(
                    "SELECT max(version) FROM file WHERE entry_id = ?${if (originalsOnly) " AND NOT processed" else ""}",
                    entryId
                ).map(asIntOrNull)
            )
        }

    suspend fun nextVersion(entryId: UUID, tx: TransactionalSession? = null): AppResult<Int> =
        latestVersion(entryId, false, tx).map { it.getOrElse { 0 } + 1 }

    suspend fun add(file: NewFileDesc, tx: TransactionalSession? = null): AppResult<FileDesc> = either {
        val version = nextVersion(file.entryId, tx).bind()
        val result = db.use(tx) {
            it.one(
                queryOf(
                    """
                        INSERT INTO file(entry_id, version, orig_filename, storage_filename, type, size, checksum, processed, info) 
                        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) 
                        RETURNING *""".trimIndent(),
                    file.entryId,
                    version,
                    file.originalFilename,
                    file.storageFilename.toString(),
                    file.type,
                    file.size().getOrElse { 0 },
                    file.checksum().getOrNull(),
                    file.processed,
                    file.info,
                ).map(FileDesc.fromRow)
            )
        }.bind()
        postProcessUpload(result)
        result
    }

    suspend fun getLatest(entryId: UUID, originalsOnly: Boolean): AppResult<FileDesc> = db.use {
        it.one(
            queryOf(
                """
                    SELECT * 
                    FROM file 
                    WHERE entry_id = ? ${if (originalsOnly) " AND NOT processed" else ""}
                    ORDER BY version DESC 
                    LIMIT 1""".trimIndent(),
                entryId
            ).map(FileDesc.fromRow)
        )
    }

    suspend fun getVersion(entryId: UUID, version: Int): AppResult<FileDesc> = db.use {
        it.one(
            queryOf(
                "SELECT * FROM file WHERE entry_id = ? AND version = ?",
                entryId,
                version
            ).map(FileDesc.fromRow)
        )
    }

    suspend fun getAllVersions(entryId: UUID): AppResult<List<FileDesc>> = db.use {
        it.many(queryOf("SELECT * FROM file WHERE entry_id = ? ORDER BY version DESC", entryId).map(FileDesc.fromRow))
    }

    suspend fun getUserVersion(entryId: UUID, version: Int, userId: UUID) = db.use {
        it.one(
            queryOf(
                """
                SELECT *
                FROM file
                JOIN entry ON entry.id = file.entry_id
                WHERE entry_id = ?
                  AND version = ?
                  AND (
                    entry.user_id = ? OR
                    (SELECT is_admin FROM appuser WHERE id = ?)
                )
            """,
                entryId,
                version,
                userId,
                userId,
            ).map(FileDesc.fromRow)
        )
    }

    fun getStoragePath(name: String): Path =
        app.config.entryDir.resolve(name)

    fun getStorageFile(name: Path): File =
        app.config.entryDir.resolve(name).toFile()

    suspend fun getAll() = db.use {
        it.many(queryOf("SELECT * FROM file").map(FileDesc.fromRow))
    }

    private fun buildStorageFilename(
        compoName: String,
        entryId: UUID,
        version: Int,
        author: String,
        title: String,
        originalFilename: String
    ): Path {
        val compo = compoName.toFilenameToken(true) ?: "compo"
        val id = "${entryId}-v${version.toString().padStart(2, '0')}"
        val authorClean = author.toFilenameToken(false)
        val titleClean = title.toFilenameToken(false)
        val extension = File(originalFilename).extension.lowercase()

        return Paths.get(compo, "$id-$authorClean-$titleClean.$extension")
    }

    suspend fun postProcessUpload(file: FileDesc) {
        if (!file.processed) {
            val streamingAudioFormats = FileFormatCategory.streamingAudio.formats().flatMap { it.extensions }
            if (streamingAudioFormats.contains(file.extension)) {
                app.workQueue.addTask(NormalizeLoudness(file))
            }
        }
    }

    suspend fun getEntryIdsWithFiles(): AppResult<List<UUID>> = db.use {
        it.many(queryOf("SELECT DISTINCT entry_id FROM file").map { it.uuid("entry_id") })
    }

}

data class NewFileDesc(
    val entryId: UUID,
    val originalFilename: String,
    val storageFilename: Path,
    val processed: Boolean,
    val info: String?
) {
    val type: String by lazy { FileDesc.getType(originalFilename) }
    val absolutePath: Path by lazy { Config.get().entryDir.resolve(storageFilename) }

    fun size(): Either<InternalServerError, Long> =
        Either.catch { Files.size(absolutePath) }.mapLeft { InternalServerError(it) }

    fun checksum(): AppResult<String> =
        FileChecksums.get(absolutePath)

}

@Serializable
data class FileDesc(
    @Serializable(with = UUIDSerializer::class)
    val entryId: UUID,
    val version: Int,
    val originalFilename: String,
    @Serializable(with = PathSerializer::class)
    val storageFilename: Path,
    val type: String,
    val size: Long,
    val uploadedAt: Instant,
    val checksum: String?,
    val processed: Boolean,
    val info: String?
) {
    val isArchive = type == ZIP_ARCHIVE
    val extension by lazy { File(originalFilename).extension.lowercase() }

    fun getStorageFile(): File = Config.get().entryDir.resolve(storageFilename).toFile()

    companion object {
        val fromRow: (Row) -> FileDesc = { row ->
            FileDesc(
                entryId = row.uuid("entry_id"),
                version = row.int("version"),
                originalFilename = row.string("orig_filename"),
                storageFilename = Paths.get(row.string("storage_filename")),
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