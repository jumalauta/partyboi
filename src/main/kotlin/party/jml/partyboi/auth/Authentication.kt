package party.jml.partyboi.auth

import arrow.core.Either
import arrow.core.toOption
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
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
            challenge { call.respondRedirect("/login") }
        }
        session<User>("admin") {
            validate { if (it.isAdmin) it else null }
            challenge { call.respondRedirect("/login?admin") }
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

fun ApplicationCall.userSession(): Either<AppError, User> =
    principal<User>()
        .toOption()
        .toEither { RedirectInterruption("/login") }
