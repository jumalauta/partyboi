package party.jml.partyboi.qrcode

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object QrCode {
    fun imageSrc(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val base64 = Base64.UrlSafe.encode(bytes)
        return "/qrcode/$base64"
    }
}