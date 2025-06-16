package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import io.ktor.http.*
import io.ktor.server.application.*
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import party.jml.partyboi.system.AppResult
import java.io.FileInputStream
import java.nio.file.Path
import java.security.MessageDigest

object FileChecksums {
    private val checksumCache = EitherCache<Path, AppError, String>()

    fun get(path: Path): AppResult<String> =
        checksumCache.memoize(path) {
            Either.catch {
                val bufferLength = 1024
                val file = path.toFile()
                FileInputStream(file).use { data ->
                    val digest = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256)
                    val buffer = ByteArray(bufferLength)
                    var read = data.read(buffer, 0, bufferLength)
                    while (read > -1) {
                        digest.update(buffer, 0, read)
                        read = data.read(buffer, 0, bufferLength)
                    }
                    String(buffer.take(32).toByteArray().encodeHex())
                }
            }.mapLeft { InternalServerError(it) }
        }
}

suspend fun ApplicationCall.processFileETag(path: AppResult<Path>, block: suspend ApplicationCall.() -> Unit) {
    val tag = request.headers["ETag"]
    if (tag != null) {
        if (path
                .flatMap { FileChecksums.get(it) }
                .map { it == tag }
                .getOrElse { false }
        ) {
            response.status(HttpStatusCode.NotModified)
            return
        }
    }
    block()
}