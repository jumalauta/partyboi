package party.jml.partyboi.system

import arrow.core.getOrElse
import kotlinx.datetime.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.StoredProperties
import kotlin.time.Duration

class TimeService(app: AppServices) : StoredProperties(app) {
    val fallbackTime = LocalDateTime(1970, 1, 1, 0, 0)
    val timeZone = property("timeZone", TimeZone.currentSystemDefault())

    suspend fun localTime(): LocalDateTime =
        timeZone
            .get()
            .map { Clock.System.now().toLocalDateTime(it) }
            .getOrElse { fallbackTime }

    suspend fun isoLocalTime(): String =
        localTime().format(LocalDateTime.Formats.ISO)

    suspend fun today(): LocalDate =
        timeZone
            .get()
            .map { Clock.System.todayIn(it) }
            .getOrElse { fallbackTime.date }

    suspend fun add(time: LocalDateTime, duration: Duration): LocalDateTime =
        timeZone
            .get()
            .map { time.toInstant(it).plus(duration).toLocalDateTime(it) }
            .getOrNull()!!
}