package party.jml.partyboi.auth

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.util.pipeline.*
import party.jml.partyboi.database.DatabasePool
import party.jml.partyboi.database.User
import party.jml.partyboi.database.UserRepository
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.RedirectInterruption

class UserService(private val db: DatabasePool) {
    private val users = UserRepository(db)

    fun currentUser(ctx: PipelineContext<Unit, ApplicationCall>): Either<AppError, User> {
        return either {
            val user = users.getUserByAddr(ctx.call.request.origin.remoteAddress).bind()
            user.toEither { RedirectInterruption("/register") }.bind()
        }
    }
}