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
            .withReferenceFilter(IMAGE)
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

    fun generateVideoPreview(input: File): Pair<File, File> {
        ensureFfmpegExists()

        return app.dockerFileShare.use(input) { sharedInput ->
            val duration = probeDurationSeconds(sharedInput)
            val thumb = app.dockerFileShare.createTempFile("videoThumb", ".webp")
            val clip = app.dockerFileShare.createTempFile("videoPreview", ".webm")
            generateAnimatedThumbnail(sharedInput, thumb, duration)
            generateClip(sharedInput, clip, duration)
            thumb.localFile to clip.localFile
        }
    }

    fun probeDuration(input: File): Double {
        ensureFfmpegExists()

        return app.dockerFileShare.use(input) { sharedInput ->
            probeDurationSeconds(sharedInput)
        }
    }

    fun generateAudioPreview(input: File): Triple<File, File, File> {
        ensureFfmpegExists()

        return app.dockerFileShare.use(input) { sharedInput ->
            val duration = probeDurationSeconds(sharedInput)
            val waveformThumb = app.dockerFileShare.createTempFile("audioWaveformThumb", ".png")
            val waveformFull = app.dockerFileShare.createTempFile("audioWaveformFull", ".png")
            val clip = app.dockerFileShare.createTempFile("audioPreview", ".webm")
            generateWaveform(sharedInput, waveformThumb, width = 400, height = 120)
            generateWaveform(sharedInput, waveformFull, width = 1200, height = 360)
            generateAudioClip(sharedInput, clip, duration)
            Triple(waveformThumb.localFile, waveformFull.localFile, clip.localFile)
        }
    }

    private fun generateAnimatedThumbnail(input: SharedFile, output: SharedFile, duration: Double) {
        val frameCount = 6
        val outputFps = 2 // each frame lasts 0.5 s → 3-second loop
        val safeDuration = duration.coerceAtLeast(0.1)
        val timestamps = (0 until frameCount).map { i ->
            ((i + 0.5) * safeDuration / frameCount).coerceIn(0.0, safeDuration)
        }
        val scaleCrop = "scale=240:160:force_original_aspect_ratio=increase,crop=240:160"

        val frameFiles = mutableListOf<SharedFile>()
        try {
            // Extract one PNG per timestamp via fast keyframe seek + -frames:v 1. Doing this as
            // separate ffmpeg invocations is more reliable than a single multi-input concat:
            // there each post-seek input streams ALL its post-T frames into the filter graph,
            // and the output ends up being the whole source video re-encoded.
            timestamps.forEach { t ->
                val frame = app.dockerFileShare.createTempFile("videoThumbFrame", ".png")
                frameFiles += frame
                runFfmpeg {
                    hideBanner()
                    seek(t)
                    input(input)
                    arg("-frames:v", "1")
                    videoFilter(scaleCrop)
                    noAudio()
                    output(frame)
                }
            }

            // Stitch the 6 single-frame PNGs into an animated WebP. setpts rebases per-frame
            // PTS so libwebp emits a constant 0.5 s delay between frames regardless of how
            // PNG defaults would have spaced them.
            val filterComplex = buildString {
                frameFiles.indices.forEach { i -> append("[$i:v]") }
                append("concat=n=$frameCount:v=1,setpts=N/($outputFps*TB)[out]")
            }
            runFfmpeg {
                hideBanner()
                frameFiles.forEach { input(it) }
                arg("-filter_complex", filterComplex)
                arg("-map", "[out]")
                arg("-r", outputFps.toString())
                loop(0)
                noAudio()
                videoCodec("libwebp")
                output(output)
            }
        } finally {
            frameFiles.forEach { it.delete() }
        }
    }

    private fun generateAudioClip(input: SharedFile, output: SharedFile, duration: Double) {
        val clipSec = minOf(30.0, duration).coerceAtLeast(0.1)
        // Start a quarter of the way into the leftover tail so we skip pure-intro silence
        // but stay before the song's ending.
        val startSec = ((duration - clipSec) * 0.25).coerceIn(0.0, (duration - clipSec).coerceAtLeast(0.0))
        val fadeSec = minOf(0.5, clipSec / 4).coerceAtLeast(0.0)
        val fadeOutStart = (clipSec - fadeSec).coerceAtLeast(0.0)
        runFfmpeg {
            hideBanner()
            seek(startSec)
            input(input)
            duration(clipSec)
            noVideo()
            arg(
                "-af",
                "afade=t=in:st=0:d=$fadeSec,afade=t=out:st=$fadeOutStart:d=$fadeSec",
            )
            audioCodec("libopus")
            arg("-b:a", "96k")
            arg("-f", "webm")
            output(output)
        }
    }

    private fun generateWaveform(input: SharedFile, output: SharedFile, width: Int, height: Int) {
        runFfmpeg {
            hideBanner()
            input(input)
            arg(
                "-filter_complex",
                "[0:a]aformat=channel_layouts=mono,showwavespic=s=${width}x${height}:colors=#ffffff",
            )
            arg("-frames:v", "1")
            output(output)
        }
    }

    private fun generateClip(input: SharedFile, output: SharedFile, duration: Double) {
        val clipSec = minOf(10.0, duration).coerceAtLeast(0.1)
        val startSec = (duration * 2.0 / 3.0).coerceIn(0.0, (duration - clipSec).coerceAtLeast(0.0))
        // Fade duration capped at a quarter of the clip so very short videos still have
        // visible content between the fades meeting in the middle.
        val fadeSec = minOf(0.5, clipSec / 4).coerceAtLeast(0.0)
        val fadeOutStart = (clipSec - fadeSec).coerceAtLeast(0.0)
        runFfmpeg {
            hideBanner()
            seek(startSec)
            input(input)
            duration(clipSec)
            videoFilter(
                "scale=-2:'min(480,ih)'," +
                        "fade=t=in:st=0:d=$fadeSec," +
                        "fade=t=out:st=$fadeOutStart:d=$fadeSec"
            )
            arg(
                "-af",
                "afade=t=in:st=0:d=$fadeSec,afade=t=out:st=$fadeOutStart:d=$fadeSec",
            )
            videoCodec("libvpx-vp9")
            arg("-crf", "32", "-b:v", "0")
            audioCodec("libopus")
            arg("-b:a", "96k")
            output(output)
        }
    }

    fun probeDurationSeconds(file: SharedFile): Double {
        val out = runFfprobe(
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "csv=p=0",
            "/files/${file.localFile.name}",
        )
        return out.lines().firstNotNullOfOrNull { it.trim().toDoubleOrNull() }
            ?: throw IllegalArgumentException("Could not parse duration from ffprobe output: $out")
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
        return runContainer(entrypoint = null, args = settings.arguments, label = "FFmpeg")
    }

    private fun runFfprobe(vararg args: String): String =
        runContainer(entrypoint = "ffprobe", args = args.toList(), label = "ffprobe")

    private fun runContainer(entrypoint: String?, args: List<String>, label: String): String {
        val client = Docker.getClient()

        val binds = listOfNotNull(
            Bind(app.dockerFileShare.sharedDir.toString(), Volume("/files"))
        )

        val hostConfig = HostConfig
            .newHostConfig()
            .withBinds(binds)

        val container = client.createContainerCmd(IMAGE)
            .withHostConfig(hostConfig)
            .apply { if (entrypoint != null) withEntrypoint(entrypoint) }
            .withCmd(args)
            .withTty(false)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .exec()

        client.startContainerCmd(container.id).exec()
        client.waitContainerCmd(container.id).start().awaitCompletion()

        log.info("Run $label with arguments: $args and bindings: $binds")

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

    fun videoFilter(expr: String) {
        arguments.add("-vf")
        arguments.add(expr)
    }

    fun videoCodec(name: String) {
        arguments.add("-c:v")
        arguments.add(name)
    }

    fun audioCodec(name: String) {
        arguments.add("-c:a")
        arguments.add(name)
    }

    fun noAudio() {
        arguments.add("-an")
    }

    fun noVideo() {
        arguments.add("-vn")
    }

    fun loop(n: Int) {
        arguments.add("-loop")
        arguments.add(n.toString())
    }

    fun seek(seconds: Double) {
        arguments.add("-ss")
        arguments.add(seconds.toString())
    }

    fun duration(seconds: Double) {
        arguments.add("-t")
        arguments.add(seconds.toString())
    }

    fun arg(vararg parts: String) {
        arguments.addAll(parts)
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