package party.jml.partyboi.data

fun String.nonEmptyString(): String? = this.ifEmpty { null }