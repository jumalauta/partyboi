package party.jml.partyboi.schedule

import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.schedule

class EventSignalEmitter(app: AppServices) : Logging() {
    private val lastCheckProperty = "EventSignalEmitter.lastCheck"
    private var lastCheckTime: LocalDateTime = app.properties
        .get(lastCheckProperty)
        .getOrNone()
        .flatten()
        .flatMap { it.localDateTime().getOrNone() }
        .getOrElse { LocalDateTime.MIN }

    private val scheduler: TimerTask = Timer().schedule(1000, 1000) {
        val now = LocalDateTime.now()
        app.events.getBetween(lastCheckTime, now).map {
            runBlocking {
                it.forEach { event ->
                    log.info("${event.name} begins")
                    app.signals.emit(event.signal())
                }
            }
        }
        lastCheckTime = now
        app.properties.set(lastCheckProperty, lastCheckTime)
    }
}