package party.jml.partyboi.data

import arrow.core.Option
import arrow.core.some
import kotliquery.Row

fun Row.optionalBoolean(columnLabel: String): Option<Boolean> =
    when (stringOrNull(columnLabel)?.take(1)?.lowercase()) {
        "t" -> true.some()
        "f" -> false.some()
        else -> arrow.core.none()
    }

fun Option<Boolean>.toDatabaseEnum(): String? =
    this.fold({ null }, { it.toString() })

fun Option<Boolean>.isTrue(): Boolean = this.isSome { it }

fun Option<Boolean>.isFalse(): Boolean = this.isSome { !it }

inline fun <reified T : Enum<T>> Row.valueOf(columnLabel: String): T =
    enumValueOf(string(columnLabel))

inline fun <reified T : Enum<T>> Row.valueOfOrNull(columnLabel: String): T? =
    stringOrNull(columnLabel)?.let { enumValueOf<T>(it) }