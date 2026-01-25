package party.jml.partyboi.system

import java.io.Closeable
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively

fun createTemporaryDirectory(): Path {
    val dir = createTempDirectory()
    dir.toFile().deleteOnExit()
    return dir
}

@OptIn(ExperimentalPathApi::class)
fun useTempDirectory(f: (Path) -> Unit) {
    val dir = createTemporaryDirectory()
    f(dir)
    dir.deleteRecursively()
}

fun createTemporaryFile(prefix: String? = null, suffix: String? = null): File {
    val file = kotlin.io.path.createTempFile(prefix, suffix).toFile()
    file.deleteOnExit()
    return file
}

suspend fun useTempFile(f: suspend (Path) -> Unit) {
    val file = kotlin.io.path.createTempFile()
    file.toFile().deleteOnExit()
    f(file)
    file.deleteIfExists()
}

class TempDir : Closeable {
    val path = createTempDirectory()

    @OptIn(ExperimentalPathApi::class)
    override fun close() {
        path.deleteRecursively()
    }
}