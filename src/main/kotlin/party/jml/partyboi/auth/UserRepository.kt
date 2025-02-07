package party.jml.partyboi.auth

import arrow.core.Either
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
import party.jml.partyboi.Config
import party.jml.partyboi.Logging
import party.jml.partyboi.admin.settings.AutomaticVoteKeys
import party.jml.partyboi.data.*
import party.jml.partyboi.db.*
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.voting.VoteKey

class UserRepository(private val app: AppServices) : Logging() {
    private val db = app.db

    init {
        createAdminUser().throwOnError()
    }

    fun getUsers(): Either<AppError, List<User>> = db.use {
        it.many(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                """,
            ).map(User.fromRow)
        )
    }

    fun getUser(name: String): Either<AppError, User> = db.use {
        it.one(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE name = ?
                """,
                name
            ).map(User.fromRow)
        )
    }

    fun getUser(id: Int): Either<AppError, User> = db.use {
        it.one(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE id = ?
                """,
                id
            ).map(User.fromRow)
        )
    }

    fun addUser(user: UserCredentials, ip: String): Either<AppError, User> = db.use {
        either {
            val createdUser = it.one(
                queryOf(
                    "insert into appuser(name, password) values (?, ?) returning *, false as voting_enabled",
                    user.name,
                    user.hashedPassword(),
                ).map(User.fromRow)
            ).bind()

            fun registerVoteKey(voteKey: VoteKey) = createdUser.copy(
                votingEnabled = app.voteKeys.insertVoteKey(createdUser.id, voteKey).isRight()
            )

            when (app.settings.automaticVoteKeys.get().bind()) {
                AutomaticVoteKeys.PER_USER -> registerVoteKey(VoteKey.user(createdUser.id))
                AutomaticVoteKeys.PER_IP_ADDRESS -> registerVoteKey(VoteKey.ipAddr(ip))
                else -> createdUser
            }
        }
    }

    fun updateUser(userId: Int, user: UserCredentials): Either<AppError, Unit> = db.transaction {
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

        // TODO: Move to an own repository
        log.info("Import ${data.voteKeys.size} vote keys")
        data.voteKeys.map {
            tx.exec(
                queryOf(
                    "INSERT INTO votekey (key, appuser_id) VALUES (?, ?)",
                    it.key,
                    it.userId,
                )
            )
        }.bindAll()
    }

    fun makeAdmin(userId: Int, state: Boolean) = db.use {
        it.updateOne(queryOf("UPDATE appuser SET is_admin = ? WHERE id = ?", state, userId))
    }

    private fun createAdminUser() = db.use {
        val password = Config.getAdminPassword()
        val admin = UserCredentials(Config.getAdminUserName(), password, password)
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
) : Principal {
    fun authenticate(plainPassword: String): Either<AppError, User> =
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
            )
        }

        val LoginError = ValidationError("name", "Invalid user name or password", "")
        val InvalidSessionError = RedirectInterruption("/login")
    }
}

data class UserCredentials(
    @property:Field(1, "User name")
    val name: String,
    @property:Field(2, "Password", presentation = FieldPresentation.secret)
    val password: String,
    @property:Field(3, "Password again", presentation = FieldPresentation.secret)
    val password2: String,
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
        val Empty = UserCredentials("", "", "")

        fun fromUser(user: User): UserCredentials = UserCredentials(
            name = user.name,
            password = "",
            password2 = "",
            isUpdate = true,
        )
    }
}

