package party.jml.partyboi.plugins

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.toOption
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import party.jml.partyboi.database.DatabasePool
import party.jml.partyboi.database.User
import party.jml.partyboi.database.UserRepository
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.RedirectInterruption
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

fun Application.configureAuthentication(db: DatabasePool) {
    val users = UserRepository(db)

    install(Authentication) {
        session<User>("user") {
            validate { session ->
                if (users.getUser(session.name).isRight()) {
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
        val secretSignKey = hex((1..64).map { Random.nextLong(0, 15) }.joinToString(""))
        cookie<User>("user_session", SessionStorageMemory()) {
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
