package party.jml.partyboi

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.*
import party.jml.partyboi.data.Filesize
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
    val instanceId: String by lazy { config.property("instance.id").getString() }
    val instanceName: String by lazy { config.property("instance.name").getString() }
    val hostName: String by lazy { config.property("instance.host").getString() }

    // Data initialization
    val adminUsername: String by lazy { config.property("init.admin.username").getString() }
    val adminPassword: String by lazy { config.property("init.admin.password").getString() }

    // Sessions
    val secretSignKey: ByteArray by lazy { hex(config.property("ktor.sessions.secretSignKey").getString()) }

    // Directories
    val filesDir by lazy { config.property("files.path").getPath() }
    val assetsDir: Path by lazy { filesDir.resolve("assets") }
    val screenshotsDir: Path by lazy { filesDir.resolve("screenshots") }

    // Database
    val dbHost: String by lazy { config.property("db.host").getString() }
    val dbPort: Int by lazy { config.property("db.port").getInt() }
    val dbDatabase: String by lazy { config.property("db.database").getString() }
    val dbUser: String by lazy { config.property("db.user").getString() }
    val dbPassword: String by lazy { config.property("db.password").getString() }

    // Email
    val brevoApiKey: String? by lazy { config.propertyOrNull("email.brevo.apiKey")?.getString()?.nonEmptyString() }
    val mockEmail: Boolean? by lazy { config.propertyOrNull("email.mock")?.getString() == "true" }

    // File uploads
    val maxFileUploadSize: Long by lazy { config.propertyOrNull("files.maxSize")?.getSize() ?: -1L }
}

fun ApplicationConfigValue.getSize(): Long {
    return Filesize.parseHumanFriendly(getString()).fold({ -1L }, { it })
}