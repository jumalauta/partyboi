package party.jml.partyboi.assets

import arrow.core.flatMap
import kotlinx.serialization.Serializable
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

    fun write(file: FileUpload): AppResult<Unit> =
        catchError {
            val target = assetsDir.resolve(file.name)
            target.parent.toFile().mkdirs()
            target
        }.flatMap {
            file.writeAndAutoExtract(it)
        }

    fun getList(): List<Asset> =
        try {
            Files.walk(assetsDir).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .map { it.relativeTo(assetsDir).toString() }
                    .toList()
                    .map { Asset(it) }
            }
        } catch (_: Throwable) {
            emptyList()
        }

    fun getList(type: String): List<Asset> =
        getList().filter { it.type == type }

    fun getFile(name: String): Path =
        assetsDir.resolve(name)

    fun exists(name: String): Boolean =
        getFile(name).toFile().exists()

    fun delete(name: String): AppResult<Unit> = catchError {
        Files.delete(assetsDir.resolve(name))
    }

    fun getChecksum(name: String): AppResult<String> =
        FileChecksums.md5sum(assetsDir.resolve(name).toFile())
}

@Serializable
data class Asset(
    val fullName: String
) {
    override fun toString(): String = fullName

    val truncatedName: String by lazy {
        val maxEntryLength = 38
        fullName
            .split('/')
            .joinToString("/") {
                if (it.length >= maxEntryLength) {
                    if (it.contains(".")) {
                        val ext = it.split('.').last()
                        val head = it.take(maxEntryLength - ext.length - 3)
                        "${head}...${ext}"
                    } else {
                        it.take(maxEntryLength - 3) + "..."
                    }
                } else it
            }
    }

    val displayName: String by lazy {
        truncatedName.replace("/", " / ")
    }

    val type: String by lazy {
        FileDesc.getType(fullName)
    }
}