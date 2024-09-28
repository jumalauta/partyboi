package party.jml.partyboi.data

object Filesize {
    fun Int.gigabytes(): Long {
        return this.toLong() * 1_000_000_000
    }

    fun Int.megabytes(): Long {
        return this.toLong() * 1_000_000
    }
}
