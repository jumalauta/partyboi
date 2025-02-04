package party.jml.partyboi.data

import org.apache.commons.lang3.RandomStringUtils

fun String.nonEmptyString(): String? = this.ifEmpty { null }

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
