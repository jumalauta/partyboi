package party.jml

import party.jml.partyboi.ffmpeg.FfmpegService
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotEquals

class FfmpegTest {
    val ffmpeg = FfmpegService()

    @Test
    fun `normalize audio file`() {
        val dir = createTempDir()
        val input = loadResourceToDisk("/files/disco.mp3", dir)
        val output = ffmpeg.normalizeLoudness(input)

        assertNotEquals(0, output.length())
    }

    private fun createTempDir(): File =
        Files.createTempDirectory("ffmpeg-test-").toFile()

    private fun createTempFile(targetDir: File, suffix: String): File {
        targetDir.deleteOnExit()
        targetDir.setReadable(true, false)
        targetDir.setWritable(true, false)
        return File.createTempFile("ffmpeg-test-", suffix, targetDir)
    }

    private fun loadResourceToDisk(resourcePath: String, targetDir: File): File {
        val inputStream = object {}.javaClass.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")

        val tempFile = createTempFile(targetDir, "." + resourcePath.split('.').last())

        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }
}