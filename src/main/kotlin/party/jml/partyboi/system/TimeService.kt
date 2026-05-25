package party.jml.partyboi.system

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlin.time.Clock
import kotlin.time.Instant
import party.jml.partyboi.AppServices
import party.jml.partyboi.AppServicesImpl
import party.jml.partyboi.Service
import java.time.format.DateTimeFormatter
import java.util.Locale.getDefault
import kotlin.time.Duration

val LOCAL_ISO_DATETIME_FORMAT = LocalDateTime.Format {
    date(LocalDate.Formats.ISO)
    char('T')
    hour()
    char(':')
    minute()
    char(':')
    second()
}

class TimeService(app: AppServices) : Service(app) {
    val fallbackTime = Instant.DISTANT_PAST
    val timeZone = property("timeZone", TimeZone.currentSystemDefault())
    val timeZoneOverrides = property<Map<LocalDate, TimeZone>>("timeZoneOverrides", emptyMap())

    suspend fun localTime(): LocalDateTime =
        Clock.System.now().toLocalDateTime(timeZone())

    suspend fun isoLocalTime(): String =
        localTime().format(LocalDateTime.Formats.ISO)

    fun today(): LocalDate =
        Clock.System.todayIn(TimeZone.currentSystemDefault())

    suspend fun timeZone(): TimeZone =
        timeZoneAt(today())

    suspend fun timeZoneAt(date: LocalDate): TimeZone =
        timeZoneOverrides.getOrNull()?.let { it[date] }
            ?: timeZone.getOrNull()
            ?: TimeZone.currentSystemDefault()


    companion object {
        fun timeZoneAt(date: LocalDate): TimeZone =
            runBlocking { AppServicesImpl.globalInstance!!.time.timeZoneAt(date) }

        fun timeZone(): TimeZone = runBlocking { AppServicesImpl.globalInstance!!.time.timeZone() }
    }
}

fun LocalDate.displayDate(): String {
    val nameOfDay = dayOfWeek.name
        .lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
    val dateStr = format(LocalDate.Format {
        day()
        char('.')
        monthNumber()
        char('.')
        year()
    })
    return "$nameOfDay $dateStr"
}


fun LocalDateTime.displayTime(): String =
    toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm"))

fun Instant.displayTime(tz: TimeZone): String =
    toLocalDateTime(tz).displayTime()

fun Instant.displayDateTime(tz: TimeZone): String {
    val time = toLocalDateTime(tz)
    return "${time.date.displayDate()} ${time.displayTime()}"
}

fun Instant.displayDuration(): String {
    return Clock.System.now().minus(this).displayDuration()
}

fun Instant.toDate(): LocalDate = toLocalDateTime(TimeService.timeZone()).date

fun Instant.toIsoString(): String = format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET)

fun Instant.toLocalIsoString(): String {
    val date = toLocalDateTime(TimeService.timeZone()).date
    return toLocalDateTime(TimeService.timeZoneAt(date)).format(LOCAL_ISO_DATETIME_FORMAT)
}

fun Instant.utcToTimeZone(tz: TimeZone): Instant = this.toLocalDateTime(tz).toInstant(TimeZone.UTC)

fun Duration.displayDuration(): String {
    if (inWholeSeconds < 60) {
        return "${inWholeSeconds} seconds"
    }
    if (inWholeMinutes < 60) {
        return "${inWholeMinutes} minutes"
    }
    if (inWholeHours < 24) {
        return "${inWholeHours} hours"
    }
    return "${inWholeDays} days"
}