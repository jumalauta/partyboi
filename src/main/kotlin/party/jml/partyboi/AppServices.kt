package party.jml.partyboi

import party.jml.partyboi.database.*
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import io.ktor.util.*

class AppServices(db: DatabasePool) {
    val sessions = SessionRepository(db)
    val compos = CompoRepository(db)
    val entries = EntryRepository(db)
    val users = UserRepository(db)
}

object Config {
    private val config = systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromResource("defaults.properties")

    private val secretSignKey = Key("sessions.secretSignKey", stringType)

    fun getSecretSignKey() = hex(config.get(secretSignKey))
}