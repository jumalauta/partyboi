package party.jml.partyboi

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.*
import party.jml.partyboi.data.nonEmptyString
import java.nio.file.Path
import java.nio.file.Paths

object Config {
    private var instance: ConfigReader? = null

    fun get(app: Application? = null): ConfigReader {
        if (instance == null) {
            if (app == null) throw RuntimeException("Application tried to use configuration before initialializing the config reader")
            instance = ConfigReader(app.environment.config)
        }
        return instance!!
    }
}

fun Application.config() = Config.get(this)

fun ApplicationConfigValue.getPath(): Path = Paths.get(getString())
fun ApplicationConfigValue.getInt(): Int = getString().toInt()

class ConfigReader(private val config: ApplicationConfig) {
    // Instance
    val instanceName: String by lazy { config.property("instance.name").getString() }
    val hostName: String by lazy { config.property("instance.host").getString() }

    // Data initialization
    val adminUsername: String by lazy { config.property("init.admin.username").getString() }
    val adminPassword: String by lazy { config.property("init.admin.password").getString() }

    // Sessions
    val secretSignKey: ByteArray by lazy { hex(config.property("ktor.sessions.secretSignKey").getString()) }

    // Directories
    val entryDir: Path by lazy { config.property("files.entries").getPath() }
    val assetsDir: Path by lazy { config.property("files.assets").getPath() }
    val screenshotsDir: Path by lazy { config.property("files.screenshots").getPath() }

    // Database
    val dbHost: String by lazy { config.property("db.host").getString() }
    val dbPort: Int by lazy { config.property("db.port").getInt() }
    val dbDatabase: String by lazy { config.property("db.database").getString() }
    val dbUser: String by lazy { config.property("db.user").getString() }
    val dbPassword: String by lazy { config.property("db.password").getString() }

    // Email
    val brevoApiKey: String? by lazy { config.propertyOrNull("email.brevo.apiKey")?.getString()?.nonEmptyString() }

    // Replication
    val replicationExportApiKey: String? by lazy {
        config.propertyOrNull("replication.export.key")?.getString()?.nonEmptyString()
    }
    val replicationImport: Option<ReplicationImport> by lazy {
        val source = config.propertyOrNull("replication.import.source")?.getString()?.nonEmptyString()
        val apiKey = config.propertyOrNull("replication.import.key")?.getString()?.nonEmptyString()
        if (source != null && apiKey != null) {
            ReplicationImport(source, apiKey).some()
        } else {
            none()
        }
    }
}

data class ReplicationImport(
    val source: String,
    val apiKey: String,
)