package party.jml.partyboi.data

import arrow.core.*
import kotliquery.Row

fun Row.optionalBoolean(columnLabel: String): Option<Boolean> {
    if (underlying.wasNull()) {
        return arrow.core.none()
    } else {
        return when (underlying.getString(columnLabel)) {
            "true" -> true.some()
            "false" -> false.some()
            else -> arrow.core.none()
        }
    }
}

fun Option<Boolean>.toDatabaseEnum(): String? =
    this.fold({ null }, { it.toString() })

fun Option<Boolean>.isTrue(): Boolean = this.isSome { it }

fun Option<Boolean>.isFalse(): Boolean = this.isSome { !it }
