package party.jml.partyboi.auth

import arrow.core.Option
import arrow.core.none
import arrow.core.recover
import arrow.core.toOption
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.RedirectInterruption
import party.jml.partyboi.system.AppResult
import kotlin.time.Duration.Companion.days

val UpdatedUser = AttributeKey<User>("UpdatedUser")

fun Application.configureAuthentication(app: AppServices) {
    install(Authentication) {
        session<User>("user") {
            validate { it }
            challenge { call.forwardToLogin() }
        }
        session<User>("userApi") {
            validate { it }
            challenge { call.respond(HttpStatusCode.Unauthorized) }
        }
        session<User>("voting") {
            validate { if (it.votingEnabled) it else null }
            challenge { call.respondRedirect("/") }
        }
        session<User>("admin") {
            validate { if (it.isAdmin) it else null }
            challenge { call.forwardToLogin() }
        }
        session<User>("adminApi") {
            validate { if (it.isAdmin) it else null }
            challenge { call.respond(HttpStatusCode.Unauthorized) }
        }
    }

    install(Sessions) {
        val secretSignKey = app.config.secretSignKey
        cookie<User>("user_session", app.sessions) {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 7.days.inWholeSeconds
            transform(SessionTransportTransformerMessageAuthentication(secretSignKey))
        }
    }
}

suspend fun ApplicationCall.forwardToLogin() {
    response.cookies.append("afterLogin", request.path())
    respondRedirect("/login")
}

suspend fun ApplicationCall.optionalUserSession(app: AppServices?): Option<User> =
    (attributes.getOrNull(UpdatedUser) ?: principal<User>())
        .toOption()
        .flatMap { user ->
            (app?.users?.consumeUserSessionReloadRequest(user.id) ?: none())
                .onSome {
                    attributes.put(UpdatedUser, it)
                    sessions.set(it)
                }
                .recover { user }
        }

suspend fun ApplicationCall.userSession(app: AppServices?): AppResult<User> =
    optionalUserSession(app).toEither { RedirectInterruption("/login") }

fun Application.publicRouting(block: Route.() -> Unit) {
    routing {
        authenticate("user", optional = true) { block() }
    }
}

fun Application.userRouting(block: Route.() -> Unit) {
    routing {
        authenticate("user") { block() }
    }
}

fun Application.userApiRouting(block: Route.() -> Unit) {
    routing {
        authenticate("userApi") { block() }
    }
}

fun Application.votingRouting(block: Route.() -> Unit) {
    routing {
        authenticate("voting") { block() }
    }
}

fun Application.adminRouting(block: Route.() -> Unit) {
    routing {
        authenticate("admin") { block() }
    }
}

fun Application.adminApiRouting(block: Route.() -> Unit) {
    routing {
        authenticate("adminApi") { block() }
    }
}
