package party.jml.partyboi.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import party.jml.partyboi.config
import kotlin.time.Duration.Companion.minutes

val LoginRateLimit = RateLimitName("login")

fun Application.configureHTTP() {
    val hostName = config().hostName
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHost(hostName, schemes = listOf("https", "http"))
    }
    install(Compression)
    install(RateLimit) {
        register(LoginRateLimit) {
            rateLimiter(limit = 10, refillPeriod = 5.minutes)
            requestKey { call ->
                call.request.origin.remoteAddress
            }
        }
    }
}
