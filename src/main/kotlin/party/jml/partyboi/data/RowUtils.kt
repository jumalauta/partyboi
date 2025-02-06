package party.jml.partyboi.data

import kotliquery.Row

fun Row.optionalBoolean(columnLabel: String): Boolean? {
    if (underlying.wasNull()) {
        return null
    } else {
        return when (underlying.getString(columnLabel)) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}