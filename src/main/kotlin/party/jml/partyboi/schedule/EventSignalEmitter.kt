package party.jml.partyboi.schedule

import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import java.time.LocalDate.EPOCH
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.schedule

class EventSignalEmitter(app: AppServices) : Logging() {
    private val defaultLastCheck = EPOCH.atTime(0, 0)
    private val lastCheck = app.properties.property("EventSignalEmitter.lastCheck", defaultLastCheck)

    private val scheduler: TimerTask = Timer().schedule(1000, 1000) {
        val now = LocalDateTime.now()
        val lastCheckTime = lastCheck.getSync().getOrElse { defaultLastCheck }
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