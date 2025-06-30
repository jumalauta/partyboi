package party.jml.partyboi.ffmpeg

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class FfmpegService() {
    fun normalizeLoudness(input: File, output: File) {
        val measurement = measureLoudness(input)
        normalizeWithMeasurement(input, output, measurement)
    }

    private fun measureLoudness(file: File): LoudnessMeasurement {
        val result = runFfmpeg(
            file,
            listOf(
                "-hide_banner",
                "-i", "/config/${file.name}",
                "-af", loudnessNormalizationFilter("print_format" to "json"),
                "-f", "null",
                "-"
            )
        )
        val regex = Regex("""(?s)\{.*?}""")
        val json = regex.find(result)?.value ?: throw IllegalArgumentException("Could not parse result: $result")
        return lenientJson.decodeFromString<LoudnessMeasurement>(json)
    }

    private fun normalizeWithMeasurement(input: File, output: File, measurement: LoudnessMeasurement) {
        val result = runFfmpeg(
            input,
            listOf(
                "-hide_banner",
                "-i", "/config/${input.name}",
                "-af", loudnessNormalizationFilter(
                    "measured_I" to measurement.input_i,
                    "measured_tp" to measurement.input_tp,
                    "measured_LRA" to measurement.input_lra,
                    "measured_thresh" to measurement.input_thresh,
                    "offset" to measurement.target_offset,
                ),
                "-y", "/config/${output.name}"
            )
        )
        println(result)
    }

    private fun runFfmpeg(input: File, args: List<String>): String {
        val client = getClient()

        val pwd = input.absoluteFile.normalize().parentFile.path

        val binds = listOf(
            Bind(pwd, Volume("/config"))
        )

        val hostConfig = HostConfig.newHostConfig().withBinds(binds)

        val container = client.createContainerCmd(IMAGE)
            .withHostConfig(hostConfig)
            .withCmd(args.toList())
            .withTty(false)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .exec()

        client.startContainerCmd(container.id).exec()
        client.waitContainerCmd(container.id).start().awaitCompletion()

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

        return logs.toString()
    }

    private fun getClient() = DockerClientBuilder.getInstance().build()

    private fun loudnessNormalizationFilter(vararg args: Pair<String, Any>): String =
        filterSettings("loudnorm", loudnessSettings + args.toMap())

    private fun filterSettings(key: String, args: Map<String, Any>): String =
        "$key=${args.map { (key, value) -> "$key=$value" }.joinToString(":")}"

    private val lenientJson = Json {
        ignoreUnknownKeys = true
    }

    companion object {
        const val IMAGE = "lscr.io/linuxserver/ffmpeg:latest"

        val loudnessSettings = mapOf(
            "I" to -23,
            "LRA" to 7,
            "tp" to -2
        )
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