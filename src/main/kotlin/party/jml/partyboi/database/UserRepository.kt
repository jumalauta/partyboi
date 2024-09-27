package party.jml.partyboi.database

import arrow.core.*
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.ValidationError
import party.jml.partyboi.form.Field

class UserRepository(private val db: DatabasePool) {
    fun getUserByAddr(clientIp: String): Either<AppError, Option<User>> {
        return db.use {
            val query = queryOf("select * from appuser where ip_addr = ?::inet", clientIp)
                .map(User.fromRow)
                .asSingle
            it.run(query).toOption()
        }
    }

    fun addUser(user: NewUser): Either<AppError, User> {
        return db.use {
            val query = queryOf("insert into appuser(ip_addr, name) values (?::inet, ?) returning *", user.ipAddr, user.name)
                .map(User.fromRow)
                .asSingle
            it.run(query) as User
        }
    }
}

data class User(val id: Int, val ipAddr: String, val name: String) {
    companion object {
        val fromRow: (Row) -> User = { row ->
            User(
                id = row.int("id"),
                ipAddr = row.string("ip_addr"),
                name = row.string("name"),
            )
        }
    }
}

data class NewUser(
    val ipAddr: String,
    @property:Field(1, "User name")
    val name: String,
) : Validateable<NewUser> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64)
    )

    companion object {
        val Empty = NewUser("", "")
    }
}