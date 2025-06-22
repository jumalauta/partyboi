package party.jml.partyboi.auth

import arrow.core.Either
import arrow.core.Option
import arrow.core.none
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.data.throwOnError
import party.jml.partyboi.email.EmailTemplates
import party.jml.partyboi.messages.MessageType
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.voting.VoteKey

class UserService(private val app: AppServices) {
    private val repository = UserRepository(app)
    private val userSessionReloadRequests = mutableSetOf<Int>()

    init {
        runBlocking {
            repository.createAdminUser().throwOnError()
        }
    }

    suspend fun getUser(userId: Int): AppResult<User> = repository.getUser(userId)
    suspend fun getUserByName(username: String): AppResult<User> = repository.getUser(username)
    suspend fun getUsers(): AppResult<List<User>> = repository.getUsers()
    suspend fun updateUser(userId: Int, user: UserCredentials): AppResult<Unit> = repository.updateUser(userId, user)
    suspend fun makeAdmin(userId: Int, isAdmin: Boolean) = repository.makeAdmin(userId, isAdmin)
    suspend fun deleteAll() = repository.deleteAll()

    suspend fun addUser(user: UserCredentials, ip: String): AppResult<User> =
        either {
            val createdUser = repository.createUser(user).bind()

            suspend fun registerVoteKey(voteKey: VoteKey) = createdUser.copy(
                votingEnabled = app.voteKeys.insertVoteKey(createdUser.id, voteKey, null).isRight()
            )

            val assignedUser = when (app.settings.automaticVoteKeys.get().bind()) {
                AutomaticVoteKeys.PER_USER -> {
                    registerVoteKey(VoteKey.user(createdUser.id))
                }

                AutomaticVoteKeys.PER_IP_ADDRESS -> {
                    registerVoteKey(VoteKey.ipAddr(ip))
                }

                AutomaticVoteKeys.PER_EMAIL -> {
                    createdUser.copy(votingEnabled = processAutomaticVoteKeyByEmail(createdUser.id).bind())
                }

                else -> createdUser
            }

            sendVerificationEmail(assignedUser)?.let { result ->
                if (result.isRight()) {
                    app.messages.sendMessage(
                        assignedUser.id, MessageType.INFO,
                        "To finish creating your account, please verify your email address by following the instructions sent to ${assignedUser.email}"
                    )
                } else {
                    app.messages.sendMessage(
                        assignedUser.id, MessageType.WARNING,
                        "Sending verification email failed. Some features may not be enabled. Contact the organizers."
                    )
                }
            }

            assignedUser
        }

    suspend fun sendVerificationEmail(user: User): Either<AppError, Unit>? =
        user.email?.let { email ->
            either {
                if (app.email.isConfigured()) {
                    app.email.sendMail(
                        EmailTemplates.emailVerification(
                            recipient = user,
                            verificationCode = repository.generateVerificationCode(user.id).bind(),
                            instanceName = app.config.instanceName,
                            hostName = app.config.hostName
                        )
                    ).bind()
                }
            }
        }?.onLeft { error ->
            app.errors.saveSafely(
                error = error.throwable ?: Error("Sending verification email failed due to an unexpected error"),
                context = user
            )
        }

    suspend fun verifyEmail(userId: Int, verificationCode: String): Either<AppError, Unit> = either {
        val expectedCode = repository.getEmailVerificationCode(userId).onLeft {
            app.messages.sendMessage(
                userId,
                MessageType.ERROR,
                "Invalid verification."
            )
        }.bind()

        if (verificationCode == expectedCode) {
            repository.setEmailVerified(userId).bind()
            app.messages.sendMessage(
                userId,
                MessageType.SUCCESS,
                "Your email has been verified successfully."
            )
            processAutomaticVoteKeyByEmail(userId).bind()
        } else {
            app.messages.sendMessage(
                userId,
                MessageType.ERROR,
                "Invalid verification."
            )
            InvalidInput("Invalid verification code")
        }
    }

    suspend fun processAutomaticVoteKeyByEmail(userId: Int): Either<AppError, Boolean> = either {
        val automaticVoteKeys = app.settings.automaticVoteKeys.get().bind()
        if (automaticVoteKeys == AutomaticVoteKeys.PER_EMAIL) {
            val email = repository.getUser(userId).bind().email
            val verifiedEmailsOnly = app.settings.verifiedEmailsOnly.get().bind()

            val eligible = !verifiedEmailsOnly || either {
                val emails = app.settings.voteKeyEmailList.get().bind()
                emails.contains(email)
            }.bind()

            if (email != null && eligible) {
                app.voteKeys.insertVoteKey(userId, VoteKey.email(email), null).bind()
                true
            } else false
        } else {
            false
        }
    }

    fun requestUserSessionReload(userId: Int) {
        userSessionReloadRequests.add(userId)
    }

    suspend fun consumeUserSessionReloadRequest(userId: Int): Option<User> =
        if (userSessionReloadRequests.contains(userId)) {
            userSessionReloadRequests.remove(userId)
            repository.getUser(userId).getOrNone()
        } else {
            none()
        }

    fun import(tx: TransactionalSession, data: DataExport) = repository.import(tx, data)
}