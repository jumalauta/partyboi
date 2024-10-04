package party.jml.partyboi.database

import arrow.core.*
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotliquery.*
import kotliquery.action.ResultQueryActionBuilder
import party.jml.partyboi.Config
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.DatabaseError
import party.jml.partyboi.errors.NotFound
import java.sql.Connection

class DatabasePool(val dataSource: HikariDataSource) {
    fun <A> use(block: (Session) -> A): Either<AppError, A> =
        Either
            .catch { sessionOf(dataSource).use(block) }
            .mapLeft { DatabaseError(it.toString()) }

    fun <A> useUnsafe(block: (Session) -> A): A =
        sessionOf(dataSource).use { block(it) }

    fun init(query: String): Unit =
        sessionOf(dataSource).use { it.run(queryOf(query.trimIndent()).asExecute) }

    fun <A> option(query: ResultQueryActionBuilder<A>): Either<AppError, Option<A>> =
        use { it.run(query.asSingle).toOption() }

    fun <A> one(query: ResultQueryActionBuilder<A>): Either<AppError, A> =
        option(query).flatMap { it.toEither { NotFound() } }

    fun <A> many(query: ResultQueryActionBuilder<A>): Either<AppError, List<A>> =
        use { it.run(query.asList) }

    fun execute(query: Query): Either<AppError, Unit> =
        use { it.run(query.asExecute) }

    fun update(query: Query): Either<AppError, Int> =
        use { it.run(query.asUpdate) }

    fun updateOne(query: Query): Either<AppError, Unit> =
        update(query).flatMap { if (it != 1) NotFound().left() else Unit.right() }
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
fun Application.getDatabasePool(): DatabasePool {
    Class.forName("org.postgresql.Driver")

    val host = Config.getDbHost()
    val port = Config.getDbPort()
    val database = Config.getDbDatabase()
    val url = "jdbc:postgresql://$host:$port/$database"

    return DatabasePool(HikariCP.default(url, Config.getDbUser(), Config.getDbPassword()))
}
