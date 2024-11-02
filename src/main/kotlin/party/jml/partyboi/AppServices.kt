package party.jml.partyboi

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import io.ktor.util.*
import io.ktor.util.logging.*
import party.jml.partyboi.admin.compos.CompoRunService
import party.jml.partyboi.assets.AssetsRepository
import party.jml.partyboi.auth.SessionRepository
import party.jml.partyboi.auth.UserRepository
import party.jml.partyboi.compos.CompoRepository
import party.jml.partyboi.data.DatabasePool
import party.jml.partyboi.data.PropertyRepository
import party.jml.partyboi.entries.EntryRepository
import party.jml.partyboi.entries.FileRepository
import party.jml.partyboi.entries.ScreenshotRepository
import party.jml.partyboi.schedule.EventRepository
import party.jml.partyboi.screen.ScreenService
import party.jml.partyboi.signals.SignalService
import party.jml.partyboi.triggers.TriggerRepository
import party.jml.partyboi.voting.VoteService
import java.nio.file.Path
import java.nio.file.Paths

class AppServices(db: DatabasePool) {
    val db: DatabasePool = db
    val users = UserRepository(db)
    val sessions = SessionRepository(db)
    val compos = CompoRepository(this)
    val entries = EntryRepository(this)
    val files = FileRepository(this)
    val votes = VoteService(this)
    val compoRun = CompoRunService(this)
    val screen = ScreenService(this)
    val screenshots = ScreenshotRepository(this)
    val properties = PropertyRepository(this)
    val events = EventRepository(this)
    val triggers = TriggerRepository(this)
    val signals = SignalService()
    val assets = AssetsRepository(this)
}

object Config {
    private val config = systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromResource("defaults.properties")

    private val secretSignKey = Key("sessions.secretSignKey", stringType)
    private val entryDir = Key("dir.entries", stringType)
    private val assetsDir = Key("dir.assets", stringType)
    private val screenshotsDir = Key("dir.screenshots", stringType)
    private val dbHost = Key("db.host", stringType)
    private val dbPort = Key("db.port", intType)
    private val dbUser = Key("db.user", stringType)
    private val dbPassword = Key("db.password", stringType)
    private val dbDatabase = Key("db.database", stringType)
    private val adminUserName = Key("admin.username", stringType)
    private val adminPassword = Key("admin.password", stringType)

    fun getSecretSignKey() = hex(config.get(secretSignKey))
    fun getEntryDir(): Path = Paths.get(config[entryDir])
    fun getAssetsDir(): Path = Paths.get(config[assetsDir])
    fun getScreenshotsDir(): Path = Paths.get(config[screenshotsDir])
    fun getDbHost() = config.getOrElse(dbHost, "localhost")
    fun getDbPort() = config.getOrElse(dbPort, 5432)
    fun getDbUser() = config.getOrElse(dbUser, "postgres")
    fun getDbPassword() = config.get(dbPassword)
    fun getDbDatabase() = config.getOrElse(dbDatabase, "default")
    fun getAdminUserName() = config.get(adminUserName)
    fun getAdminPassword() = config.get(adminPassword)
}

abstract class Logging {
    val log = KtorSimpleLogger(this.javaClass.name)
}