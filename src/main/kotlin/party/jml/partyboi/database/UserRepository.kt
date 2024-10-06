package party.jml.partyboi.database

import arrow.core.*
import io.ktor.server.auth.*
import kotlinx.html.InputType
import kotliquery.Row
import kotliquery.queryOf
import org.mindrot.jbcrypt.BCrypt
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.RedirectInterruption
import party.jml.partyboi.errors.ValidationError
import party.jml.partyboi.form.Field

class UserRepository(private val db: DatabasePool) {
    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS appuser (
                id SERIAL PRIMARY KEY,
                name text NOT NULL UNIQUE,
                password text NOT NULL,
                is_admin boolean DEFAULT false
            );
        """)
    }

    fun getUser(name: String): Either<AppError, User> = db.use {
        it.one(queryOf("select * from appuser where name = ?", name).map(User.fromRow))
    }

    fun addUser(user: NewUser): Either<AppError, User> = db.use {
        it.one(queryOf(
            "insert into appuser(name, password) values (?, ?) returning *",
            user.name,
            user.hashedPassword(),
        ).map(User.fromRow))
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
    @property:Field(2, "Password", type = InputType.password)
    val password: String,
    @property:Field(3, "Password again", type = InputType.password)
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

