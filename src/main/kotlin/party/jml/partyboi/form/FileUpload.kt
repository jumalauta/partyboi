package party.jml.partyboi.form

import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import party.jml.partyboi.Config
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.data.MapCollector
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.createTemporaryFile
import party.jml.partyboi.zip.ZipUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

data class FileUpload(
    val name: String,
    val tempFile: File,
) {
    fun writeEntry(storageFilename: Path): AppResult<Unit> =
        write(Config.get().filesDir.resolve(storageFilename).toFile())

    fun write(storageFilename: Path): AppResult<Unit> =
        write(storageFilename.toFile())

    fun write(file: File): AppResult<Unit> = try {
        File(file.parent).mkdirs()
        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit.right()
    } catch (err: Error) {
        InternalServerError(err).left()
    }

    fun writeAndAutoExtract(storageFilename: Path): AppResult<Unit> =
        if (storageFilename.extension.lowercase() == "zip") {
            extract(storageFilename)
        } else {
            write(storageFilename)
        }

    fun extract(storageFilename: Path): AppResult<Unit> = either {
        val tempFile = createTemporaryFile("tmp", ".zip")
        write(tempFile).bind()

        val storageDir = storageFilename.parent.resolve(storageFilename.nameWithoutExtension)
        ZipUtils.extract(tempFile, storageDir).bind()
    }

    fun toByteArray(): ByteArray = tempFile.readBytes()

    val isDefined = name.isNotEmpty()

    companion object {
        val Empty = createTestData("", 0)

        fun createTestData(filename: String, length: Int) = fromByteArray(
            filename,
            ByteArray(length)
        )

        fun fromResource(self: Any, filename: String) = self::class.java.getResource(filename)?.let {
            fromByteArray(
                Paths.get(filename).fileName.toString(),
                it.readBytes()
            )
        }

        fun fromByteArray(filename: String, bytes: ByteArray): FileUpload {
            val tempFile = createTemporaryFile()
            tempFile.writeBytes(bytes)
            return FileUpload(filename, tempFile)
        }
    }
}

// A sensible default upload limit for forms that only carry text fields — Ktor's formFieldLimit
// bounds both the whole multipart body and each individual part, so this caps non-file form fields.
const val MAX_FORM_FIELD_SIZE = 1024L * 1024L // 1 MB

// Fallback upload limit when files.maxSize is not configured, so uploads are never truly unbounded.
const val DEFAULT_MAX_UPLOAD_SIZE = 2L * 1024 * 1024 * 1024 // 2 GB

// The size limit handed to Ktor's receiveMultipart(formFieldLimit). Ktor uses it to bound the whole
// multipart body and each individual part, so it must be at least the largest allowed file — and
// must never be unlimited (-1), which is what an unconfigured files.maxSize used to produce.
fun multipartSizeLimit(configuredMaxFileSize: Long): Long =
    if (configuredMaxFileSize > 0) configuredMaxFileSize else DEFAULT_MAX_UPLOAD_SIZE

suspend fun MultiPartData.collect(): Pair<Map<String, List<String>>, Map<String, List<FileUpload>>> {
    val stringParams = MapCollector<String, String>()
    val fileParams = MapCollector<String, FileUpload>()

    forEachPart { part ->
        part.name?.let { name ->
            when (part) {
                is PartData.FormItem -> {
                    stringParams.add(name, part.value)
                    part.dispose()
                }

                is PartData.FileItem -> {
                    val tempFile = createTemporaryFile()
                    part.provider().copyAndClose(tempFile.writeChannel())
                    fileParams.add(
                        name, FileUpload(
                            name = part.originalFileName
                                ?: throw IllegalArgumentException("File name missing for parameter '$name'"),
                            tempFile = tempFile,
                        )
                    )
                }

                else -> part.dispose()
            }
        }
    }

    return Pair(stringParams.toMap(), fileParams.toMap())
}