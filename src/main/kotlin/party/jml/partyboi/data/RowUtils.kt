package party.jml.partyboi.data

import kotliquery.Row

fun Row.optionalBooleanOrNull(columnLabel: String): Boolean? =
    when (stringOrNull(columnLabel)?.take(1)?.lowercase()) {
        "t" -> true
        "f" -> false
        else -> null
    }

fun Boolean?.toDatabaseEnum(): String? = this?.toString()
