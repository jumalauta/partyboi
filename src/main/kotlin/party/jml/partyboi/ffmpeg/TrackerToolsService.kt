package party.jml.partyboi.ffmpeg

import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import java.io.File

/**
 * Renders tracker modules (.mod, .s3m, .xm, .it, .mptm, ...) to WAV using the
 * `ilkkahanninen/partyboi-trackertools` Docker image. The image mounts the shared directory at
 * /work and takes positional `input output` arguments.
 */
class TrackerToolsService(app: AppServices) : Service(app) {
    fun convertToWav(input: File): File {
        Docker.ensureImageExists(IMAGE, log, platform = PLATFORM)

        return app.dockerFileShare.use(input) { sharedInput ->
            val output = app.dockerFileShare.createTempFile("trackerWav", ".wav")
            Docker.runContainer(
                image = IMAGE,
                sharedDir = app.dockerFileShare.sharedDir,
                mountPoint = "/work",
                entrypoint = null,
                args = listOf("/work/${sharedInput.localFile.name}", "/work/${output.localFile.name}"),
                label = "trackertools",
                log = log,
                platform = PLATFORM,
            )
            output.localFile
        }
    }

    companion object {
        const val IMAGE = "ilkkahanninen/partyboi-trackertools:latest"

        // The image is published for linux/amd64 only; pin it so Apple Silicon hosts run it under
        // emulation instead of failing to find a matching manifest.
        const val PLATFORM = "linux/amd64"
    }
}
