package party.jml.partyboi.schedule

import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import java.util.*
import kotlin.concurrent.schedule

class EventSignalEmitter(app: AppServices) : Service(app) {
    private val lastCheck = runBlocking {
        property(
            key = "lastCheck",
            value = Clock.System.now(),
            private = true,
        )
    }

    private val scheduler: TimerTask = Timer().schedule(1000, 1000) {
        runBlocking {
            val now = Clock.System.now()
            val lastCheckTime = lastCheck.get().getOrElse { app.time.fallbackTime }
            app.events.getBetween(lastCheckTime, now).map {
                it.forEach { event ->
                    log.info("${event.name} begins")
                    app.signals.emit(event.signal())
                }
            }
            lastCheck.set(now)
        }
    }
}