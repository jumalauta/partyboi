package party.jml.partyboi.schedule

import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.StoredProperties
import java.util.*
import kotlin.concurrent.schedule

class EventSignalEmitter(app: AppServices) : StoredProperties(app) {
    private val lastCheck = property("lastCheck", app.time.localTimeSync())

    private val scheduler: TimerTask = Timer().schedule(1000, 1000) {
        val now = app.time.localTimeSync()
        val lastCheckTime = lastCheck.getSync().getOrElse { app.time.fallbackTime }
        app.events.getBetween(lastCheckTime, now).map {
            runBlocking {
                it.forEach { event ->
                    log.info("${event.name} begins")
                    app.signals.emit(event.signal())
                }
            }
        }
        lastCheck.setSync(now)
    }
}