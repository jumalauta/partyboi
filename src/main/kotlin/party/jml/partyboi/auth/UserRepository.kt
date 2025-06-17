package party.jml.partyboi.auth

import arrow.core.*
import arrow.core.raise.either
import io.ktor.server.auth.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import org.mindrot.jbcrypt.BCrypt
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.*
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asOptionalString
import party.jml.partyboi.db.DbBasicMappers.asString
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.suspendEither
import party.jml.partyboi.voting.VoteKey

class UserRepository(private val app: AppServices) : Logging() {
    private val db = app.db
    private val userSessionReloadRequests = mutableSetOf<Int>()

    init {
        runBlocking {
            createAdminUser().throwOnError()
        }
    }

    suspend fun getUsers(): AppResult<List<User>> = db.use {
        it.many(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    email,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                """,
            ).map(User.fromRow)
        )
    }

    suspend fun getUser(name: String): AppResult<User> = db.use {
        it.one(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    email,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE name = ?
                """,
                name
            ).map(User.fromRow)
        )
    }

    suspend fun getUser(id: Int): AppResult<User> = db.use {
        it.one(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    email,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE id = ?
                """,
                id
            ).map(User.fromRow)
        )
    }

    suspend fun addUser(user: UserCredentials, ip: String): AppResult<User> = db.use {
        suspendEither {
            val createdUser = it.one(
                queryOf(
                    """
                        INSERT INTO appuser (name, password, email)
                        	VALUES (?, ?, ?)
                        RETURNING
                        	*, FALSE AS voting_enabled
                    """.trimIndent(),
                    user.name,
                    user.hashedPassword(),
                    user.email.nonEmptyString()
                ).map(User.fromRow)
            ).bind()

            suspend fun registerVoteKey(voteKey: VoteKey) = createdUser.copy(
                votingEnabled = app.voteKeys.insertVoteKey(createdUser.id, voteKey, null).isRight()
            )

            val assignedUser = when (app.settings.automaticVoteKeys.get().bind()) {
                AutomaticVoteKeys.PER_USER -> registerVoteKey(VoteKey.user(createdUser.id))
                AutomaticVoteKeys.PER_IP_ADDRESS -> registerVoteKey(VoteKey.ipAddr(ip))
                AutomaticVoteKeys.PER_EMAIL ->
                    createdUser.email?.let { email ->
                        val emails = app.settings.voteKeyEmailList.get().fold({ emptyList() }, { it })
                        if (emails.contains(email)) registerVoteKey(VoteKey.email(email))
                        else null
                    } ?: createdUser

                else -> createdUser
            }

            sendVerificationEmail(assignedUser)

            assignedUser
        }
    }

    suspend fun updateUser(userId: Int, user: UserCredentials): AppResult<Unit> = db.transaction {
        either {
            if (user.password.isNotEmpty()) {
                it.updateOne(
                    queryOf(
                        "UPDATE appuser SET password = ? WHERE id = ?",
                        user.hashedPassword(),
                        userId
                    )
                ).bind()
            }

            it.updateOne(
                queryOf(
                    "UPDATE appuser SET name = ? WHERE id = ?",
                    user.name,
                    userId
                )
            ).bind()
        }
    }

    suspend fun deleteUserByName(name: String) = db.use {
        it.exec(queryOf("DELETE FROM appuser WHERE name = ?", name))
    }

    suspend fun deleteAll() = db.use {
        it.exec(queryOf("DELETE FROM appuser"))
    }

    suspend fun sendVerificationEmail(user: User): Either<AppError, Unit>? = (db.use {
        user.email?.let { email ->
            either {
                if (app.email.isConfigured()) {
                    val verificationCode = generateVerificationCode(user.id).bind()
                    val instanceName = app.config.instanceName
                    val sender = listOf(instanceName, "Partyboi").distinct().joinToString(" / ")

                    app.email.sendMail(email, "Verify your email to $instanceName") {
                        body {
                            h1 { +"Hello, ${user.name}!" }
                            p { +"Welcome to the $instanceName!" }
                            p { +"To ensure your maximal enjoyment, please verify your email by clicking the following link." }
                            p {
                                a(href = "${app.config.hostName}/verify/${user.id}/$verificationCode") {
                                    +"Verify your email"
                                }
                            }
                            p {
                                +"Br, $sender team"
                            }
                        }
                    }.bind()
                } else {
                    setEmailVerified(user.id).bind()
                }
            }
        }
    })?.onLeft { error ->
        app.errors.saveSafely(
            error = error.throwable ?: Error("Sending verification email failed due to an unexpected error"),
            context = user
        )
    }

    suspend fun verifyEmail(userId: Int, verificationCode: String): Either<AppError, Unit> = db.use {
        either {
            val expectedCode = it.one(
                queryOf(
                    "SELECT verification_code FROM appuser WHERE id = ?",
                    userId
                ).map(asOptionalString)
            ).bind()

            if (verificationCode == expectedCode) {
                setEmailVerified(userId).bind()
            } else {
                InvalidInput("Invalid verification code")
            }
        }
    }

    suspend fun setEmailVerified(userId: Int): AppResult<Unit> = db.use {
        it.exec(
            queryOf(
                """
                    UPDATE appuser
                    SET
                        verification_code = NULL,
                        email_verified = true
                    WHERE id = ?
                """.trimIndent(),
                userId
            )
        )
    }

    suspend fun generateVerificationCode(userId: Int): AppResult<String> = db.use {
        val code = randomStringId(32)
        it.one(
            queryOf(
                """
            UPDATE appuser
            SET verification_code = ?
            WHERE id = ?
            RETURNING verification_code
        """.trimIndent(),
                code,
                userId
            ).map(asString)
        )
    }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.users.size} users")
        data.users.map {
            tx.exec(
                queryOf(
                    "INSERT INTO appuser (id, name, password, is_admin) VALUES (?, ?, ?, ?)",
                    it.id,
                    it.name,
                    it.hashedPassword,
                    it.isAdmin,
                )
            )
        }.bindAll()
    }

    suspend fun makeAdmin(userId: Int, state: Boolean) = db.use {
        it.updateOne(queryOf("UPDATE appuser SET is_admin = ? WHERE id = ?", state, userId))
    }

    fun requestUserSessionReload(userId: Int) {
        userSessionReloadRequests.add(userId)
    }

    suspend fun consumeUserSessionReloadRequest(userId: Int): Option<User> =
        if (userSessionReloadRequests.contains(userId)) {
            userSessionReloadRequests.remove(userId)
            getUser(userId).getOrNone()
        } else {
            none()
        }

    private suspend fun createAdminUser() = db.use {
        val password = app.config.adminPassword
        val admin = UserCredentials(app.config.adminUsername, password, password, "")
        it.exec(
            queryOf(
                "INSERT INTO appuser(name, password, is_admin) VALUES (?, ?, true) ON CONFLICT DO NOTHING",
                admin.name,
                admin.hashedPassword()
            )
        )
    }
}

@Serializable
data class User(
    val id: Int,
    val name: String,
    val hashedPassword: String,
    val isAdmin: Boolean,
    val votingEnabled: Boolean,
    val email: String?,
) : Principal {
    fun authenticate(plainPassword: String): AppResult<User> =
        if (BCrypt.checkpw(plainPassword, hashedPassword)) {
            this.right()
        } else {
            LoginError.left()
        }

    companion object {
        val fromRow: (Row) -> User = { row ->
            User(
                id = row.int("id"),
                name = row.string("name"),
                hashedPassword = row.string("password"),
                isAdmin = row.boolean("is_admin"),
                votingEnabled = row.boolean("voting_enabled"),
                email = row.stringOrNull("email"),
            )
        }

        val LoginError = ValidationError("name", "Invalid user name or password", "")
        val InvalidSessionError = RedirectInterruption("/login")
    }
}

data class UserCredentials(
    @property:Field(
        order = 1,
        label = "User name"
    )
    val name: String,

    @property:Field(
        order = 2,
        label = "Password",
        presentation = FieldPresentation.secret
    )
    val password: String,

    @property:Field(
        order = 3,
        label = "Password again",
        presentation = FieldPresentation.secret
    )
    val password2: String,

    @property:Field(
        order = 4,
        label = "Email",
        presentation = FieldPresentation.email,
        description = "Optional but recommended"
    )
    val email: String,

    @property:Field(presentation = FieldPresentation.hidden)
    val isUpdate: Boolean = false,
) : Validateable<UserCredentials> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64),
        expectEqual("password2", password2, password)
    ) + (if (isUpdate && password.isEmpty()) emptyList() else listOf(
        expectMinLength("password", password, 8),
        expectMinLength("password2", password2, 8),
    ))

    fun hashedPassword(): String = BCrypt.hashpw(password, BCrypt.gensalt())

    companion object {
        val Empty = UserCredentials("", "", "", "")

        fun fromUser(user: User): UserCredentials = UserCredentials(
            name = user.name,
            password = "",
            password2 = "",
            isUpdate = true,
            email = user.email ?: "",
        )
    }
}

