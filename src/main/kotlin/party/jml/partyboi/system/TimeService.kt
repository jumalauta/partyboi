package party.jml.partyboi.system

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import party.jml.partyboi.AppServices
import party.jml.partyboi.AppServicesImpl
import party.jml.partyboi.Service
import java.time.format.DateTimeFormatter
import java.util.Locale.getDefault
import kotlin.time.Duration

class TimeService(app: AppServices) : Service(app) {
    val fallbackTime = Instant.DISTANT_PAST
    val timeZone = property("timeZone", TimeZone.currentSystemDefault())

    fun localTime(): LocalDateTime =
        Clock.System.now().toLocalDateTime(timeZone())

    fun isoLocalTime(): String =
        localTime().format(LocalDateTime.Formats.ISO)

    fun today(): LocalDate =
        Clock.System.todayIn(timeZone())

    companion object {
        fun timeZone(): TimeZone = runBlocking {
            AppServicesImpl.globalInstance?.time?.timeZone?.getOrNull() ?: TimeZone.currentSystemDefault()
        }

        fun isoOffset(): String = timeZone().offsetAt(Clock.System.now()).toString()
    }
}

fun LocalDate.displayDate(): String {
    val nameOfDay = dayOfWeek.name
        .lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
    val dateStr = format(LocalDate.Format {
        dayOfMonth()
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