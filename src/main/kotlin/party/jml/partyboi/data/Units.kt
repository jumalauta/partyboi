package party.jml.partyboi.data

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
}

fun Double.toDecimals(decimalPlaces: Int): String =
    "%.${decimalPlaces}f".format(this)
