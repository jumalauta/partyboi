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
            ?: kotlinx.datetime.Clock.System.now().toDate().year
    }
}