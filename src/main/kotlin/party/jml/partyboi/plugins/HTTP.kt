package party.jml.partyboi.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.ratelimit.*
import party.jml.partyboi.config
import kotlin.time.Duration.Companion.minutes

val LoginRateLimit = RateLimitName("login")

fun Application.configureHTTP() {
    val hostName = config().hostName.removePrefix("https://").removePrefix("http://")
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
    install(createRouteScopedPlugin("SecurityHeaders") {
        onCallRespond { call ->
            if (call.request.path().startsWith("/screen")) {
                call.response.header("X-Frame-Options", "SAMEORIGIN")
            } else {
                call.response.header("X-Frame-Options", "DENY")
            }
            call.response.header("X-Content-Type-Options", "nosniff")
            call.response.header("Referrer-Policy", "strict-origin-when-cross-origin")
            call.response.header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        }
    })
    install(RateLimit) {
        register(LoginRateLimit) {
            rateLimiter(limit = 10, refillPeriod = 5.minutes)
            requestKey { call ->
                call.request.origin.remoteAddress
            }
        }
    }
}
