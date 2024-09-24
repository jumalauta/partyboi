package party.jml.partyboi.database

import arrow.core.Either
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotliquery.HikariCP
import kotliquery.Session
import kotliquery.sessionOf
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.DatabaseError
import java.sql.Connection

class DatabasePool(val dataSource: HikariDataSource) {
    fun <A> use(block: (Session) -> A): Either<AppError, A> {
        return Either
            .catch { sessionOf(dataSource).use(block) }
            .mapLeft { DatabaseError(it.toString()) }
    }
}

/**
 * Makes a connection to a Postgres database.
 *
 * In order to connect to your running Postgres process,
 * please specify the following parameters in your configuration file:
 * - postgres.url -- Url of your running database process.
 * - postgres.user -- Username for database connection
 * - postgres.password -- Password for database connection
 *
 * If you don't have a database process running yet, you may need to [download]((https://www.postgresql.org/download/))
 * and install Postgres and follow the instructions [here](https://postgresapp.com/).
 * Then, you would be able to edit your url,  which is usually "jdbc:postgresql://host:port/database", as well as
 * user and password values.
 *
 *
 * @param embedded -- if [true] defaults to an embedded database for tests that runs locally in the same process.
 * In this case you don't have to provide any parameters in configuration file, and you don't have to run a process.
 *
 * @return [Connection] that represent connection to the database. Please, don't forget to close this connection when
 * your application shuts down by calling [Connection.close]
 * */
fun Application.getDatabasePool(embedded: Boolean): DatabasePool {
    Class.forName("org.postgresql.Driver")

    return DatabasePool(
        if (embedded) {
            HikariCP.default("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
        } else {
            val url = environment.config.property("postgres.url").getString()
            val user = environment.config.property("postgres.user").getString()
            val password = environment.config.property("postgres.password").getString()

            HikariCP.default(url, user, password)
        }
    )
}
