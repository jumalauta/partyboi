package party.jml.partyboi.ffmpeg

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import java.io.File


class FfmpegService(app: AppServices) : party.jml.partyboi.Service(app) {
    fun ensureFfmpegExists() {
        val client = Docker.getClient()

        val imageExists = client.listImagesCmd()
            .withImageNameFilter(IMAGE)
            .exec()
            .any { it.repoTags?.contains(IMAGE) == true }

        if (!imageExists) {
            log.info("$IMAGE does not exist, pulling it...")
            client.pullImageCmd(IMAGE)
                .withTag("latest")
                .exec(object :
                    ResultCallback.Adapter<PullResponseItem>() {})
                .awaitCompletion()
        }
    }

    fun normalizeLoudness(input: File): File {
        ensureFfmpegExists()

        return app.dockerFileShare.use(input) { sharedInput ->
            val measurement = measureLoudness(sharedInput)
            val sharedOutput = app.dockerFileShare.createTempFile("loudnessNormalization", ".flac")
            normalizeByMeasurement(sharedInput, sharedOutput, measurement)

            sharedOutput.localFile
        }
    }

    private fun measureLoudness(file: SharedFile): LoudnessMeasurement =
        runFfmpeg {
            hideBanner()
            input(file)
            audioFilter(
                "loudnorm",
                "I" to -14,
                "LRA" to 7,
                "tp" to -2,
                "print_format" to "json"
            )
            format(FfmpegDsl.Format.None)
            pipeOutput()
        }.extractJson<LoudnessMeasurement>()

    private fun normalizeByMeasurement(
        input: SharedFile,
        output: SharedFile,
        measurement: LoudnessMeasurement
    ): String =
        runFfmpeg {
            hideBanner()
            input(input)
            audioFilter(
                "loudnorm",
                "I" to -14,
                "LRA" to 7,
                "tp" to -2,
                "measured_I" to measurement.input_i,
                "measured_tp" to measurement.input_tp,
                "measured_LRA" to measurement.input_lra,
                "measured_thresh" to measurement.input_thresh,
                "offset" to measurement.target_offset,
                "linear" to "true"
            )
            output(output)
        }

    private fun runFfmpeg(dsl: FfmpegDsl.() -> Unit): String {
        val settings = FfmpegDsl { dsl() }
        val client = Docker.getClient()

        val binds = listOfNotNull(
            Bind(app.dockerFileShare.sharedDir.toString(), Volume("/files"))
        )

        val hostConfig = HostConfig
            .newHostConfig()
            .withBinds(binds)

        val container = client.createContainerCmd(IMAGE)
            .withHostConfig(hostConfig)
            .withCmd(settings.arguments)
            .withTty(false)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .exec()

        client.startContainerCmd(container.id).exec()
        client.waitContainerCmd(container.id).start().awaitCompletion()

        log.info("Run FFmpeg with arguments: ${settings.arguments} and bindings: $binds")

        val logs = StringBuilder()
        client.logContainerCmd(container.id)
            .withStdOut(true)
            .withStdErr(true)
            .withTimestamps(false)
            .exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    logs.append(String(frame.payload))
                }
            }).awaitCompletion()

        log.info(logs.toString())

        client.removeContainerCmd(container.id).exec()

        return logs.toString()
    }

    inline fun <reified T> String.extractJson(): T {
        val regex = Regex("""(?s)\{.*?}""")
        val json = regex.find(this)?.value ?: throw IllegalArgumentException("Could not parse result: $this")
        return lenientJson.decodeFromString<T>(json)
    }

    val lenientJson = Json {
        ignoreUnknownKeys = true
    }

    companion object {
        const val IMAGE = "lscr.io/linuxserver/ffmpeg:latest"
    }
}

class FfmpegDsl(dsl: FfmpegDsl.() -> Unit) {
    val arguments: MutableList<String> = mutableListOf()

    init {
        dsl()
    }

    fun hideBanner() {
        arguments.add("-hide_banner")
    }

    fun input(file: SharedFile) {
        arguments.add("-i")
        arguments.add("/files/${file.localFile.name}")
    }

    fun output(file: SharedFile) {
        arguments.add("-y")
        arguments.add("/files/${file.localFile.name}")
    }

    fun audioFilter(filterName: String, vararg args: Pair<String, Any>) {
        arguments.add("-af")
        arguments.add("$filterName=${args.joinToString(":") { (key, value) -> "$key=$value" }}")
    }

    fun format(containerFormat: Format) {
        arguments.add("-f")
        arguments.add(containerFormat.value)
    }

    fun pipeOutput() {
        arguments.add("-")
    }

    enum class Format(val value: String) {
        None("null"),
    }
}

@Serializable
data class LoudnessMeasurement(
    val input_i: String,
    val input_tp: String,
    val input_lra: String,
    val input_thresh: String,
    val target_offset: String,
)