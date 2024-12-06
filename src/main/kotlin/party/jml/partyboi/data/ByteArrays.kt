package party.jml.partyboi.data

fun ByteArray.encodeHex(): CharArray {
    val digits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    val out = CharArray(size shl 1)
    var i = 0
    var j = 0
    while (i < size) {
        val byte = this[i].toInt()
        out[j++] = digits[0xF0 and byte ushr 4]
        out[j++] = digits[0x0F and byte]
        i++
    }
    return out
}