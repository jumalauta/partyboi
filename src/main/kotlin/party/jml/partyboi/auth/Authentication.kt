package party.jml.partyboi.auth

import arrow.core.Either
import arrow.core.Option
import arrow.core.toOption
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.RedirectInterruption
import kotlin.time.Duration.Companion.days

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
        val secretSignKey = Config.getSecretSignKey()
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

fun ApplicationCall.optionalUserSession(): Option<User> =
    principal<User>().toOption()

fun ApplicationCall.userSession(): Either<AppError, User> =
    optionalUserSession().toEither { RedirectInterruption("/login") }

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
