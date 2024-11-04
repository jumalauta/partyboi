@file:OptIn(ExperimentalEncodingApi::class)

package party.jml.partyboi.qrcode

import arrow.core.getOrElse
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.templates.respondPage
import party.jml.partyboi.theme.AppTheme
import party.jml.partyboi.theme.ScreenTheme
import qrcode.QRCode
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun Application.configureQrCodeRouting() {
    routing {
        get("/qrcode/{base64}") {
            either {
                val base64 = call.parameterString("base64").bind()
                val content = Base64.UrlSafe.decode(base64.toByteArray()).toString(Charsets.UTF_8)
                val colors =
                    if (call.request.queryParameters["theme"] == "screen") ScreenTheme.qrCode else AppTheme.qrCode
                val code = QRCode.ofRoundedSquares()
                    .withColor(colors.front)
                    .withBackgroundColor(colors.back)
                    .build(content)
                val pngBytes = code.render().getBytes()
                call.respondBytes(pngBytes, contentType = ContentType("image", "png"))
            }.getOrElse { call.respondPage(it) }
        }
    }
}