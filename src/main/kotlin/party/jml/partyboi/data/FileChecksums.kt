package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import io.ktor.http.*
import io.ktor.server.application.*
import party.jml.partyboi.system.AppResult
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.security.MessageDigest

object FileChecksums {
    private val checksumCache = EitherCache<Path, AppError, String>()

    fun md5sum(file: File): AppResult<String> = Either.catch {
        val buffer = ByteArray(1024 * 4)
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        // Convert bytes to hex string
        md.digest().joinToString("") { "%02x".format(it) }
    }.mapLeft { InternalServerError(it) }


//    fun get(path: Path): AppResult<String> =
//        checksumCache.memoize(path) {
//            Either.catch {
//                val bufferLength = 1024
//                val file = path.toFile()
//                FileInputStream(file).use { data ->
//                    val digest = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256)
//                    val buffer = ByteArray(bufferLength)
//                    var read = data.read(buffer, 0, bufferLength)
//                    while (read > -1) {
//                        digest.update(buffer, 0, read)
//                        read = data.read(buffer, 0, bufferLength)
//                    }
//                    String(buffer.take(32).toByteArray().encodeHex())
//                }
//            }.mapLeft { InternalServerError(it) }
//        }
}

suspend fun ApplicationCall.processFileETag(path: AppResult<Path>, block: suspend ApplicationCall.() -> Unit) {
    val tag = request.headers["ETag"]
    if (tag != null) {
        if (path
                .flatMap { FileChecksums.md5sum(it.toFile()) }
                .map { it == tag }
                .getOrElse { false }
        ) {
            response.status(HttpStatusCode.NotModified)
            return
        }
    }
    block()
}