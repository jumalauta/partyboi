package party.jml.partyboi.syncharness

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class DockerStack(
    private val composeFile: String = "docker-compose-sync-test.yml",
    private val projectName: String = "partyboi-sync-test",
    private val projectDir: File = File(System.getProperty("user.dir")),
) {
    private fun base(): List<String> = listOf(
        "docker", "compose",
        "-f", composeFile,
        "-p", projectName,
    )

    private fun run(args: List<String>, timeoutSec: Long = 600) {
        val cmd = base() + args
        println("\$ ${cmd.joinToString(" ")}")
        val proc = ProcessBuilder(cmd)
            .directory(projectDir)
            .inheritIO()
            .start()
        if (!proc.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            error("docker compose ${args.joinToString(" ")} timed out after ${timeoutSec}s")
        }
        val code = proc.exitValue()
        if (code != 0) {
            error("docker compose ${args.joinToString(" ")} exited with code $code")
        }
    }

    fun up() {
        // Ensure bind-mount target dirs exist and are writable before docker creates them as root.
        val filesMaster = projectDir.toPath().resolve("build/sync-test/files-master")
        val filesRemote = projectDir.toPath().resolve("build/sync-test/files-remote")
        Files.createDirectories(filesMaster)
        Files.createDirectories(filesRemote)
        // Make them world-writable so the postgres/partyboi containers (running as their own UIDs) can write.
        runCatching { filesMaster.toFile().setWritable(true, false) }
        runCatching { filesRemote.toFile().setWritable(true, false) }

        run(listOf("up", "-d", "--build", "--wait"), timeoutSec = 900)
    }

    fun down() {
        runCatching {
            run(listOf("down", "-v", "--remove-orphans"))
        }.onFailure { println("docker compose down failed (continuing teardown): ${it.message}") }
        wipeFilesDirs()
    }

    fun dumpLogs(service: String) {
        runCatching {
            run(listOf("logs", "--no-color", "--tail=200", service))
        }
    }

    private fun wipeFilesDirs() {
        listOf(
            projectDir.toPath().resolve("build/sync-test/files-master"),
            projectDir.toPath().resolve("build/sync-test/files-remote"),
        ).forEach { dir ->
            runCatching {
                if (Files.exists(dir)) {
                    Files.walk(dir).use { stream ->
                        stream.sorted(Comparator.reverseOrder()).forEach { p: Path ->
                            runCatching { Files.delete(p) }
                        }
                    }
                }
            }
        }
    }
}
