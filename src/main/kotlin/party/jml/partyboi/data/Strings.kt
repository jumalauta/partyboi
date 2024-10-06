package party.jml.partyboi.data

fun String.nonEmptyString(): String? = this.ifEmpty { null }

fun String.toFilenameToken(removeSpaces: Boolean): String? =
    this
        .lowercase()
        .replace(" ", if (removeSpaces) "" else "_")
        .replace('ä', 'a')
        .replace('ö', 'o')
        .replace('å', 'a')
        .replace(Regex("\\W+"), "-")
        .take(32)
        .nonEmptyString()