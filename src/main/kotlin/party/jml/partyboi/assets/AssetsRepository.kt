package party.jml.partyboi.assets

import arrow.core.flatMap
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.FileChecksums
import party.jml.partyboi.data.catchError
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.system.AppResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

class AssetsRepository(app: AppServices) {
    private val assetsDir = app.config.assetsDir

    init {
        assetsDir.toFile().mkdirs()
    }

    suspend fun write(file: FileUpload): AppResult<Unit> =
        catchError {
            val target = assetsDir.resolve(file.name)
            target.parent.toFile().mkdirs()
            target
        }.flatMap {
            file.writeAndAutoExtract(it)
        }

    fun getList(): List<String> =
        try {
            Files.walk(assetsDir).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .map { it.relativeTo(assetsDir).toString() }
                    .toList()
            }
        } catch (_: Throwable) {
            emptyList()
        }

    fun getList(type: String): List<String> =
        getList().filter { FileDesc.getType(it) == type }

    fun getFile(name: String): Path =
        assetsDir.resolve(name)

    fun exists(name: String): Boolean =
        getFile(name).toFile().exists()

    fun delete(name: String): AppResult<Unit> = catchError {
        Files.delete(assetsDir.resolve(name))
    }

    fun getChecksum(name: String): AppResult<String> =
        FileChecksums.get(assetsDir.resolve(name))
}
