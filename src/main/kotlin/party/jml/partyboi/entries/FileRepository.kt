package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.DbBasicMappers.asIntOrNull
import party.jml.partyboi.data.one
import party.jml.partyboi.data.option
import party.jml.partyboi.data.toFilenameToken
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.io.path.absolutePathString

class FileRepository(private val app: AppServices) {
    private val db = app.db

    init {
        db.init("""
           CREATE TABLE IF NOT EXISTS file (
                entry_id integer REFERENCES entry(id) ON DELETE CASCADE,
                version integer,
                orig_filename text NOT NULL,
                storage_filename text NOT NULL,
                type text NOT NULL,
                size numeric NOT NULL,
                uploaded_at timestamp with time zone NOT NULL DEFAULT now(),
                CONSTRAINT file_pkey PRIMARY KEY (entry_id, version)
            ) 
        """)
    }

    fun makeStorageFilename(entry: Entry, originalFilename: String, tx: TransactionalSession? = null): Either<AppError, Path> = either {
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

    fun makeCompoRunFileOrDirName(fileDesc: FileDesc, entry: Entry, compo: Compo, targetDir: Path): Path {
        val compoName = compo.name.toFilenameToken(true) ?: "compo-${compo.id}"
        val authorClean = entry.author.toFilenameToken(false)
        val titleClean = entry.title.toFilenameToken(false)
        val extension = if (fileDesc.isArchive) "" else ".${fileDesc.extension}"
        val order = entry.runOrder.toString().padStart(2, '0')

        return Paths.get(targetDir.absolutePathString(), compoName, "$order-$authorClean-$titleClean$extension")
    }

    fun latestVersion(entryId: Int, tx: TransactionalSession? = null): Either<AppError, Option<Int>> =
        db.use(tx) {
            it.option(queryOf("SELECT max(version) FROM file WHERE entry_id = ?", entryId).map(asIntOrNull))
        }

    fun nextVersion(entryId: Int, tx: TransactionalSession? = null): Either<AppError, Int> =
        latestVersion(entryId, tx).map { it.getOrElse { 0 } + 1 }

    fun add(file: NewFileDesc, tx: TransactionalSession? = null): Either<AppError, Unit> = either {
        val version = nextVersion(file.entryId, tx).bind()
        val fileSize = 0 // TODO
        db.use(tx) {
            it.execute(queryOf(
                "INSERT INTO file(entry_id, version, orig_filename, storage_filename, type, size) VALUES(?, ?, ?, ?, ?, ?)",
                file.entryId,
                version,
                file.originalFilename,
                file.storageFilename.toString(),
                file.type,
                fileSize,
            ))
        }
    }

    fun getLatest(entryId: Int): Either<AppError, FileDesc> = db.use {
        it.one(queryOf("SELECT * FROM file WHERE entry_id = ? ORDER BY version DESC LIMIT 1", entryId).map(FileDesc.fromRow))
    }

    private fun buildStorageFilename(compoName: String, entryId: Int, version: Int, author: String, title: String, originalFilename: String): Path {
        val compo = compoName.toFilenameToken(true) ?: "compo"
        val id = "${entryId}-v${version.toString().padStart(2, '0')}"
        val authorClean = author.toFilenameToken(false)
        val titleClean = title.toFilenameToken(false)
        val extension = File(originalFilename).extension.lowercase()

        return Paths.get(compo, "$id-$authorClean-$titleClean.$extension")
    }
}

data class NewFileDesc(
    val entryId: Int,
    val originalFilename: String,
    val storageFilename: Path,
) {
    val type: String by lazy {
        when (val extension = File(originalFilename).extension.lowercase()) {
            "png", "jpg", "jpeg", "gif", "webp" -> "image"
            "mp3", "ogg", "flac", "wav" -> "sound"
            "zip" -> "zip"
            else -> extension
        }
    }
}

data class FileDesc(
    val entryId: Int,
    val version: Int,
    val originalFilename: String,
    val storageFilename: Path,
    val type: String,
    val size: Long,
    val uploadedAt: LocalDateTime,
) {
    val isArchive = type == "zip"
    val extension by lazy { File(originalFilename).extension.lowercase() }

    fun getStorageFile(): File = Config.getEntryDir().resolve(storageFilename).toFile()

    companion object {
        val fromRow: (Row) -> FileDesc = { row ->
            FileDesc(
                entryId = row.int("entry_id"),
                version = row.int("version"),
                originalFilename = row.string("orig_filename"),
                storageFilename = Paths.get(row.string("storage_filename")),
                type = row.string("type"),
                size = row.long("size"),
                uploadedAt = row.localDateTime("uploaded_at"),
            )
        }
    }
}
