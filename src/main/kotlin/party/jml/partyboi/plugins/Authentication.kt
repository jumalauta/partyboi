package party.jml.partyboi.plugins

import arrow.core.Either
import arrow.core.toOption
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.database.User
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.RedirectInterruption
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

fun Application.configureAuthentication(app: AppServices) {
    install(Authentication) {
        session<User>("user") {
            validate { session ->
                if (app.users.getUser(session.name).isRight()) {
                    session
                } else {
                    null
                }
            }
            challenge {
                call.respondRedirect("/login")
            }
        }
    }

    install(Sessions) {
        val secretSignKey = hex("0f823487adb2749201aed46b480")
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
