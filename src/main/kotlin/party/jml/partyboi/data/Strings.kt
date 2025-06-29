package party.jml.partyboi.data

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import org.apache.commons.lang3.RandomStringUtils
import java.util.Locale.getDefault
import java.util.regex.Pattern

fun String.nonEmptyString(): String? = this.ifEmpty { null }

fun String?.nonEmptyStringOption(): Option<String> = if (this == null || this.isEmpty()) none() else this.some()

fun String.toFilenameToken(removeSpaces: Boolean, maxLength: Int = 32): String? =
    this
        .lowercase()
        .replace(" ", if (removeSpaces) "" else "_")
        .replace('ä', 'a')
        .replace('ö', 'o')
        .replace('å', 'a')
        .replace(Regex("\\W+"), "-")
        .take(maxLength)
        .nonEmptyString()

fun randomShortId() = randomStringId(3) + "-" + randomStringId(3)

fun randomStringId(length: Int): String = RandomStringUtils.random(length, true, false).uppercase()

fun String.toLabel(): String {
    val tokens = split(Regex("(?<=[a-z])(?=[A-Z])"))
    tokens.joinToString(" ") { it.lowercase() }
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
}

fun String.isValidEmailAddress(): Boolean = Pattern.compile(
    "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]|[\\w-]{2,}))@"
            + "((([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
            + "[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\."
            + "([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
            + "[0-9]{1,2}|25[0-5]|2[0-4][0-9]))|"
            + "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$"
).matcher(this).matches()