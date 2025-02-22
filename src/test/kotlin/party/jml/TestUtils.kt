package party.jml

import java.math.BigInteger
import java.security.MessageDigest

object TestUtils {
    fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(bytes)).toString(16).padStart(32, '0')
    }
}