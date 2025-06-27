package party.jml.partyboi.form

import arrow.core.left
import arrow.core.right
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import party.jml.partyboi.Config
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.data.MapCollector
import party.jml.partyboi.system.AppResult
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

data class FileUpload(
    val name: String,
    val fileItem: PartData.FileItem,
) {
    fun write(storageFilename: Path): AppResult<Unit> {
        return try {
            val source = fileItem.streamProvider()
            val file = Config.get().entryDir.resolve(storageFilename).toFile()
            File(file.parent).mkdirs()
            file.outputStream().use { out ->
                while (true) {
                    val bytes = source.readNBytes(1024)
                    if (bytes.isEmpty()) break
                    out.write(bytes)
                }
                source.close()
            }
            fileItem.dispose()
            Unit.right()
        } catch (err: Error) {
            InternalServerError(err).left()
        }
    }

    fun toByteArray(): ByteArray {
        val source = fileItem.streamProvider()
        val bytes = source.readAllBytes()
        fileItem.dispose()
        return bytes
    }

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

        fun fromByteArray(filename: String, bytes: ByteArray) = FileUpload(
            filename,
            PartData.FileItem(
                { ByteReadPacket(bytes, 0, bytes.size) },
                {},
                Headers.Empty
            )
        )
    }
}

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
                    fileParams.add(
                        name, FileUpload(
                            name = part.originalFileName ?: throw Error("File name missing for parameter '$name'"),
                            fileItem = part,
                        )
                    )
                }

                else -> part.dispose()
            }
        }
    }

    return Pair(stringParams.toMap(), fileParams.toMap())
}