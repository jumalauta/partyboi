package party.jml.partyboi.ffmpeg

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
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
}