package party.jml.partyboi.assets

import arrow.core.Either
import arrow.core.flatMap
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.catchError
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.form.FileUpload
import java.nio.file.Files
import kotlin.io.path.name

class AssetsRepository(app: AppServices) {
    val assetsDir = Config.getAssetsDir()

    fun write(file: FileUpload): Either<AppError, Unit> =
        catchError {
            val target = assetsDir.resolve(file.name)
            target.parent.toFile().mkdirs()
            target
        }.flatMap {
            file.write(it)
        }

    fun getList(): List<String> =
        try {
            Files.list(assetsDir).map { it.name }.toList()
        } catch (_: Throwable) {
            emptyList()
        }

    fun getList(type: String): List<String> =
        getList().filter { FileDesc.getType(it) == type }

    fun delete(name: String): Either<AppError, Unit> = catchError {
        Files.delete(assetsDir.resolve(name))
    }

}
