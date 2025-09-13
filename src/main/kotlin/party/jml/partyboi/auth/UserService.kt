package party.jml.partyboi.auth

import arrow.core.Either
import arrow.core.Option
import arrow.core.none
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.UserCredentials.Companion.hashPassword
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.data.throwOnError
import party.jml.partyboi.email.EmailTemplates
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Label
import party.jml.partyboi.form.Presentation
import party.jml.partyboi.messages.MessageType
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.validation.EmailAddress
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import party.jml.partyboi.voting.VoteKey
import java.util.*

class UserService(private val app: AppServices) {
    private val userRepository = UserRepository(app)
    private val passwordResetRepository = PasswordResetRepository(app)
    private val userSessionReloadRequests = mutableSetOf<UUID>()

    init {
        runBlocking {
            userRepository.createAdminUser().throwOnError()
        }
    }

    suspend fun getUser(userId: UUID): AppResult<User> = userRepository.getUser(userId)
    suspend fun getUserByName(username: String): AppResult<User> = userRepository.getUser(username)
    suspend fun getUsers(): AppResult<List<User>> = userRepository.getUsers()

    suspend fun updateUser(userId: UUID, user: UserCredentials): AppResult<Unit> = either {
        val oldUser = userRepository.getUser(userId).bind()
        userRepository.updateUser(userId, user)
            .mapLeft {
                if (it.message.contains("duplicate key value")) {
                    ValidationError("email", "This email has been registered already", user.email)
                } else it
            }
            .bind()

        if (oldUser.email != user.email) {
            userRepository.setEmailVerified(userId, false)
            val updatedUser = userRepository.getUser(userId).bind()
            if (sendVerificationEmail(updatedUser)?.bind() == true) {
                app.messages.sendMessage(
                    userId, MessageType.INFO,
                    "To finish updating your email address, please verify it by following the instructions sent to ${updatedUser.email}"
                )
            }
        }
    }

    suspend fun makeAdmin(userId: UUID, isAdmin: Boolean) = userRepository.makeAdmin(userId, isAdmin)
    suspend fun deleteAll() = userRepository.deleteAll()

    suspend fun addUser(user: UserCredentials, ip: String): AppResult<User> =
        either {
            val createdUser = userRepository.createUser(user).bind()

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

    suspend fun sendVerificationEmail(user: User): Either<AppError, Boolean>? =
        user.email?.let { email ->
            either {
                if (app.email.isConfigured()) {
                    app.email.sendMail(
                        EmailTemplates.emailVerification(
                            recipient = user,
                            verificationCode = userRepository.generateVerificationCode(user.id).bind(),
                            instanceName = app.config.instanceName,
                            hostName = app.config.hostName
                        )
                    ).bind()
                    true
                } else false
            }
        }?.onLeft { error ->
            app.errors.saveSafely(
                error = error.throwable ?: Error("Sending verification email failed due to an unexpected error"),
                context = user
            )
        }

    suspend fun verifyEmail(userId: UUID, verificationCode: String): Either<AppError, Unit> = either {
        val expectedCode = userRepository.getEmailVerificationCode(userId).onLeft {
            app.messages.sendMessage(
                userId,
                MessageType.ERROR,
                "Invalid verification."
            )
        }.bind()

        if (verificationCode == expectedCode) {
            userRepository.setEmailVerified(userId).bind()
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

    suspend fun processAutomaticVoteKeyByEmail(userId: UUID): Either<AppError, Boolean> = either {
        val automaticVoteKeys = app.settings.automaticVoteKeys.get().bind()
        if (automaticVoteKeys == AutomaticVoteKeys.PER_EMAIL) {
            val user = userRepository.getUser(userId).bind()
            val verifiedEmailsOnly = app.settings.verifiedEmailsOnly.get().bind()

            val eligible = either {
                val emails = app.settings.voteKeyEmailList.get().bind()
                if (emails.contains(user.email)) {
                    if (verifiedEmailsOnly) user.emailVerified else true
                } else {
                    false
                }
            }.bind()

            if (user.email != null && eligible) {
                app.voteKeys.insertVoteKey(userId, VoteKey.email(user.email), null).bind()
                requestUserSessionReload(userId)
                true
            } else false
        } else {
            false
        }
    }

    fun requestUserSessionReload(userId: UUID) {
        userSessionReloadRequests.add(userId)
    }

    suspend fun consumeUserSessionReloadRequest(userId: UUID): Option<User> =
        if (userSessionReloadRequests.contains(userId)) {
            userSessionReloadRequests.remove(userId)
            userRepository.getUser(userId).getOrNone()
        } else {
            none()
        }

    suspend fun generatePasswordResetCode(email: String): AppResult<String> = either {
        val user = userRepository.findUserByEmail(email).bind()
        passwordResetRepository.generatePasswordResetCode(user.id).bind()
    }

    suspend fun verifyPasswordResetCode(code: String): AppResult<Unit> = either {
        passwordResetRepository.getPasswordResetUserId(code).bind()
    }

    suspend fun resetPassword(code: String, newPassword: String): AppResult<UUID> = either {
        val userId = passwordResetRepository.getPasswordResetUserId(code).bind()
        val hashedPassword = hashPassword(newPassword)
        userRepository.changePassword(userId, hashedPassword).bind()
        passwordResetRepository.invalidateCode(code).bind()
        app.messages.sendMessage(userId, MessageType.SUCCESS, "Your password has been changed successfully.").bind()
        userId
    }
}

data class PasswordResetForm(
    @Label("Email")
    @Presentation(FieldPresentation.email)
    @NotEmpty
    @EmailAddress
    val email: String,
) : Validateable<PasswordResetForm> {
    companion object {
        val Empty = PasswordResetForm("")
    }
}

data class NewPasswordForm(
    @Field(presentation = FieldPresentation.hidden)
    val code: String,

    @Field(
        label = "Password",
        presentation = FieldPresentation.secret
    )
    val password: String,

    @Field(
        label = "Password again",
        presentation = FieldPresentation.secret
    )
    val password2: String,
) : Validateable<NewPasswordForm> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectEqual("password2", password2, password),
    )

    companion object {
        fun empty(code: String) = NewPasswordForm(code, "", "")
    }
}