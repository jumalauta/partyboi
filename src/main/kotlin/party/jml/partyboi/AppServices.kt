package party.jml.partyboi

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import io.ktor.util.*
import party.jml.partyboi.admin.compos.CompoRunService
import party.jml.partyboi.auth.SessionRepository
import party.jml.partyboi.auth.UserRepository
import party.jml.partyboi.compos.CompoRepository
import party.jml.partyboi.data.DatabasePool
import party.jml.partyboi.entries.EntryRepository
import party.jml.partyboi.entries.FileRepository
import party.jml.partyboi.voting.VoteRepository
import java.nio.file.Path
import java.nio.file.Paths

class AppServices(db: DatabasePool) {
    val db: DatabasePool = db
    val users = UserRepository(db)
    val sessions = SessionRepository(db)
    val compos = CompoRepository(db)
    val entries = EntryRepository(this)
    val files = FileRepository(this)
    val votes = VoteRepository(db)
    val compoRun = CompoRunService(this)
    val screen = ScreenService(this)
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
    fun getEntryDir(): Path = Paths.get(config[entryDir])
    fun getDbHost() = config.getOrElse(dbHost, "localhost")
    fun getDbPort() = config.getOrElse(dbPort, 5432)
    fun getDbUser() = config.getOrElse(dbUser, "postgres")
    fun getDbPassword() = config.get(dbPassword)
    fun getDbDatabase() = config.getOrElse(dbDatabase, "default")
}