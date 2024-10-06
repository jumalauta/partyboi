package party.jml.partyboi.database

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.toFilenameToken
import party.jml.partyboi.database.DbBasicMappers.asIntOrNull
import party.jml.partyboi.errors.AppError
import java.io.File
import java.time.LocalDateTime

class FileRepository(private val app: AppServices) {
    private val db = app.db

    init {
        db.init("""
           CREATE TABLE IF NOT EXISTS file (
                entry_id integer REFERENCES entry(id),
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

    fun makeStorageFilename(entry: Entry, originalFilename: String, tx: TransactionalSession? = null) = either {
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
                file.storageFilename,
                file.type,
                fileSize,
            ))
        }
    }

    private fun buildStorageFilename(compoName: String, entryId: Int, version: Int, author: String, title: String, originalFilename: String): String {
        val compo = compoName.toFilenameToken(true)
        val id = "${entryId}-v${version.toString().padStart(2, '0')}"
        val authorClean = author.toFilenameToken(false)
        val titleClean = title.toFilenameToken(false)
        val extension = File(originalFilename).extension.lowercase()

        return "$compo/$id-$authorClean-$titleClean.$extension"
    }
}

data class NewFileDesc(
    val entryId: Int,
    val originalFilename: String,
    val storageFilename: String,
) {
    val type: String by lazy {
        val extension = File(originalFilename).extension.lowercase()
        when (extension) {
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
    val type: String,
    val size: Long,
    val uploadedAt: LocalDateTime,
) {
    companion object {
        val fromRow: (Row) -> FileDesc = { row ->
            FileDesc(
                entryId = row.int("entry_id"),
                version = row.int("version"),
                originalFilename = row.string("orig_filename"),
                type = row.string("type"),
                size = row.long("size"),
                uploadedAt = row.localDateTime("uploaded_at"),
            )
        }
    }
}