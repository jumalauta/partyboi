package party.jml.partyboi

import party.jml.partyboi.database.*
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import io.ktor.util.*

class AppServices(db: DatabasePool) {
    val users = UserRepository(db)
    val sessions = SessionRepository(db)
    val compos = CompoRepository(db)
    val entries = EntryRepository(db)
    val votes = VoteRepository(db)
}

object Config {
    private val config = systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromResource("defaults.properties")

    private val secretSignKey = Key("sessions.secretSignKey", stringType)
    private val entryDir = Key("entries.files", stringType)
    private val dbHost = Key("db.host", stringType)
    private val dbPort = Key("db.port", intType)
    private val dbUser = Key("db.user", stringType)
    private val dbPassword = Key("db.password", stringType)
    private val dbDatabase = Key("db.database", stringType)

    fun getSecretSignKey() = hex(config.get(secretSignKey))
    fun getEntryDir() = config.get(entryDir)
    fun getDbHost() = config.getOrElse(dbHost, "localhost")
    fun getDbPort() = config.getOrElse(dbPort, 5432)
    fun getDbUser() = config.getOrElse(dbUser, "postgres")
    fun getDbPassword() = config.get(dbPassword)
    fun getDbDatabase() = config.getOrElse(dbDatabase, "default")
}