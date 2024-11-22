package party.jml.partyboi.auth

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import io.ktor.server.auth.*
import kotliquery.Row
import kotliquery.queryOf
import org.mindrot.jbcrypt.BCrypt
import party.jml.partyboi.Config
import party.jml.partyboi.data.*
import party.jml.partyboi.db.DatabasePool
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.one
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation

class UserRepository(private val db: DatabasePool) {
    init {
        createAdminUser()
    }

    fun getUser(name: String): Either<AppError, User> = db.use {
        it.one(queryOf("select * from appuser where name = ?", name).map(User.fromRow))
    }

    fun addUser(user: NewUser): Either<AppError, User> = db.use {
        it.one(
            queryOf(
                "insert into appuser(name, password) values (?, ?) returning *",
                user.name,
                user.hashedPassword(),
            ).map(User.fromRow)
        )
    }

    private fun createAdminUser() = db.use {
        val password = Config.getAdminPassword()
        val admin = NewUser(Config.getAdminUserName(), password, password)
        it.exec(
            queryOf(
                "INSERT INTO appuser(name, password, is_admin) VALUES (?, ?, true) ON CONFLICT DO NOTHING",
                admin.name,
                admin.hashedPassword()
            )
        )
    }
}

data class User(
    val id: Int,
    val name: String,
    val hashedPassword: String,
    val isAdmin: Boolean,
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
                isAdmin = row.boolean("is_admin")
            )
        }

        val LoginError = ValidationError("name", "Invalid user name or password", "")
        val InvalidSessionError = RedirectInterruption("/login")
    }
}

data class NewUser(
    @property:Field(1, "User name")
    val name: String,
    @property:Field(2, "Password", presentation = FieldPresentation.secret)
    val password: String,
    @property:Field(3, "Password again", presentation = FieldPresentation.secret)
    val password2: String,
) : Validateable<NewUser> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64),
        expectMinLength("password", password, 8),
        expectMinLength("password2", password2, 8),
        expectEqual("password2", password2, password)
    )

    fun hashedPassword(): String = BCrypt.hashpw(password, BCrypt.gensalt())

    companion object {
        val Empty = NewUser("", "", "")
    }
}

