package party.jml.partyboi.ffmpeg

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.ktor.util.logging.*
import java.io.File
import java.net.InetAddress
import java.time.Duration

object Docker {
    fun getClient(): DockerClient {
        val config = DefaultDockerClientConfig
            .createDefaultConfigBuilder()
            .build()

        val httpClient: DockerHttpClient? = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()

        return DockerClientImpl.getInstance(config, httpClient)
    }

    fun inspectSelf(): InspectContainerResponse? = try {
        val containerId = InetAddress.getLocalHost().hostName
        val client = getClient()
        client.inspectContainerCmd(containerId).exec()
    } catch (_: NotFoundException) {
        null
    }

    fun ensureImageExists(image: String, log: Logger, platform: String? = null) {
        val client = getClient()

        val imageExists = client.listImagesCmd()
            .withReferenceFilter(image)
            .exec()
            .any { it.repoTags?.contains(image) == true }

        if (!imageExists) {
            log.info("$image does not exist, pulling it...")
            // Split the repository from the tag: docker-java's pullImageCmd expects the bare
            // repository, with the tag supplied separately. A bare repository (no ':') pulls latest.
            val tagSeparator = image.lastIndexOf(':')
            val repository = if (tagSeparator >= 0) image.substring(0, tagSeparator) else image
            val tag = if (tagSeparator >= 0) image.substring(tagSeparator + 1) else "latest"
            client.pullImageCmd(repository)
                .withTag(tag)
                // Single-arch images (e.g. amd64-only) have no manifest for the host arch on Apple
                // Silicon; pinning the platform lets Docker pull and run it under emulation.
                .apply { if (platform != null) withPlatform(platform) }
                .exec(object : ResultCallback.Adapter<PullResponseItem>() {})
                .awaitCompletion()
        }
    }

    fun runContainer(
        image: String,
        sharedDir: File,
        mountPoint: String,
        entrypoint: String?,
        args: List<String>,
        label: String,
        log: Logger,
        platform: String? = null,
    ): String {
        val client = getClient()

        val binds = listOfNotNull(
            Bind(sharedDir.toString(), Volume(mountPoint))
        )

        val hostConfig = HostConfig
            .newHostConfig()
            .withBinds(binds)

        val container = client.createContainerCmd(image)
            .withHostConfig(hostConfig)
            .apply { if (entrypoint != null) withEntrypoint(entrypoint) }
            .apply { if (platform != null) withPlatform(platform) }
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
}