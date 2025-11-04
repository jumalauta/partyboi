package party.jml.partyboi.ffmpeg

import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.nonEmptyString
import java.io.File
import java.util.*

class DockerFileShare(app: AppServices) : Service(app) {
    val sharedDir: File by lazy {
        val localShared = app.config.sharedDir.toFile()
        Docker.inspectSelf()?.let { inspection ->
            inspection.mounts?.find { it.destination == localShared }?.source?.let { File(it) }
        } ?: localShared
    }

    fun createTempFile(prefix: String = "temp", suffix: String = ".file"): SharedFile {
        val name = "${prefix}-${UUID.randomUUID()}${suffix}"
        val localFile = app.config.sharedDir.resolve(name).toFile()
        localFile.deleteOnExit()

        return SharedFile(
            hostFile = sharedDir.resolve(name),
            localFile = localFile,
        )
    }

    fun share(file: File): SharedFile {
        val sharedFile = createTempFile(suffix = file.extension.nonEmptyString()?.let { ".$it" }.orEmpty())
        sharedFile.copyFrom(file)
        return sharedFile
    }

    fun <T> use(file: File, f: (SharedFile) -> T): T {
        val sharedFile = share(file)
        return try {
            f(sharedFile)
        } finally {
            sharedFile.delete()
        }
    }
}

data class SharedFile(
    val hostFile: File,
    val localFile: File,
) {
    fun copyFrom(file: File, overwrite: Boolean = false) = file.copyTo(localFile, overwrite)
    fun delete() = localFile.delete()
}