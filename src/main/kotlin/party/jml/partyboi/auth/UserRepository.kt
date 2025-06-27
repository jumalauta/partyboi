package party.jml.partyboi.auth

import arrow.core.Option
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import org.mindrot.jbcrypt.BCrypt
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.data.randomStringId
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asOptionalString
import party.jml.partyboi.db.DbBasicMappers.asString
import party.jml.partyboi.form.*
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.system.AppResult

class UserRepository(private val app: AppServices) : Logging() {
    private val db = app.db

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
                    email_verified,
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
                    email_verified,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE name = ?
                """,
                name
            ).map(User.fromRow)
        )
    }

    suspend fun findUserByEmail(email: String): AppResult<User> = db.use {
        it.one(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    email,
                    email_verified,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE email = ?
                """,
                email
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
                    email_verified,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE id = ?
                """,
                id
            ).map(User.fromRow)
        )
    }

    suspend fun createUser(user: UserCredentials) = db.use {
        it.one(
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
        )
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

    suspend fun changePassword(userId: Int, hashedPassword: String) = db.use {
        it.updateOne(
            queryOf(
                "UPDATE appuser SET password = ? WHERE id = ?",
                hashedPassword,
                userId
            )
        )
    }

    suspend fun deleteAll() = db.use {
        it.exec(queryOf("DELETE FROM appuser"))
    }

    suspend fun getEmailVerificationCode(userId: Int) = db.use {
        it.one(
            queryOf(
                "SELECT verification_code FROM appuser WHERE id = ?",
                userId
            ).map(asOptionalString)
        )
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


    suspend fun createAdminUser() = db.use {
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
    val emailVerified: Boolean,
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
                emailVerified = row.boolean("email_verified"),
            )
        }

        val LoginError = ValidationError("name", "Invalid user name or password", "")
    }
}

data class UserCredentials(
    @Label("User name")
    val name: String,

    @Label("Password")
    @Presentation(FieldPresentation.secret)
    val password: String,

    @Label("Password again")
    @Presentation(FieldPresentation.secret)
    val password2: String,

    @Label("Email")
    @Presentation(FieldPresentation.email)
    @Description("Optional but recommended")
    val email: String,

    @Hidden
    val isUpdate: Boolean = false,
) : Validateable<UserCredentials> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64),
        expectEqual("password2", password2, password),
        expectValidEmail("email", email),
    ) + (if (isUpdate && password.isEmpty()) emptyList() else listOf(
        expectMinLength("password", password, 8),
        expectMinLength("password2", password2, 8),
    ))

    fun hashedPassword(): String = hashPassword(password)

    companion object {
        val Empty = UserCredentials("", "", "", "")

        fun fromUser(user: User): UserCredentials = UserCredentials(
            name = user.name,
            password = "",
            password2 = "",
            isUpdate = true,
            email = user.email ?: "",
        )

        fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())
    }
}

