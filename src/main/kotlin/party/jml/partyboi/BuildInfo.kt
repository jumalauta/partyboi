package party.jml.partyboi

import party.jml.partyboi.system.toDate
import java.util.*

object BuildInfo {
    val timestamp: String by lazy {
        val props = Properties()
        val stream = BuildInfo::class.java.classLoader.getResourceAsStream("build-info.properties")
        if (stream != null) {
            props.load(stream)
            props.getProperty("build.timestamp", "unknown")
        } else {
            "dev"
        }
    }

    val buildYear: Int by lazy {
        timestamp.substringBefore("-").toIntOrNull()
            ?: kotlin.time.Clock.System.now().toDate().year
    }

    private val assetVersion: String by lazy {
        timestamp.filter { it.isLetterOrDigit() }
    }

    /**
     * Cache-busting URL for a bundled static asset: the query changes on every build,
     * so browsers re-fetch CSS/JS after a deploy instead of serving a stale cache.
     * Not for user-uploaded assets — those change independently of builds.
     */
    fun asset(path: String): String = "$path?v=$assetVersion"
}