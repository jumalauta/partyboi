package party.jml.partyboi.data

import arrow.core.left
import arrow.core.right
import party.jml.partyboi.system.AppResult

object Filesize {
    fun Int.gigabytes(): Long {
        return this.toLong() * 1_000_000_000
    }

    fun Int.megabytes(): Long {
        return this.toLong() * 1_000_000
    }

    fun humanFriendly(bytes: Long): String {
        if (bytes < 1000) {
            return "$bytes B"
        }
        val kilobytes = bytes.toDouble() / 1000.0
        if (kilobytes < 1000) {
            return "${kilobytes.toDecimals(1)} kB"
        }
        val megabytes = kilobytes / 1000.0
        if (megabytes < 1000) {
            return "${megabytes.toDecimals(1)} MB"
        }
        return "${(megabytes / 1000.0).toDecimals(1)} GB"
    }

    fun parseHumanFriendly(size: String): AppResult<Long> {
        val regex = Regex("""^\s*([\d.]+)\s*([kmgtp]?i?b?)?\s*$""", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(size) ?: return InvalidInput("Invalid file size: $size").left()

        val number = match.groupValues[1].toDouble()
        val unit = match.groupValues[2].lowercase()

        val factor = when (unit) {
            "", "b" -> 1L
            "k", "kb" -> 1_000L
            "ki", "kib" -> 1L shl 10
            "m", "mb" -> 1_000_000L
            "mi", "mib" -> 1L shl 20
            "g", "gb" -> 1_000_000_000L
            "gi", "gib" -> 1L shl 30
            "t", "tb" -> 1_000_000_000_000L
            "ti", "tib" -> 1L shl 40
            "p", "pb" -> 1_000_000_000_000_000L
            "pi", "pib" -> 1L shl 50
            else -> return InvalidInput("Unknown unit: $unit").left()
        }

        return (number * factor).toLong().right()
    }
}

fun Double.toDecimals(decimalPlaces: Int): String =
    "%.${decimalPlaces}f".format(this)
