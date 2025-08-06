package party.jml.partyboi.templates

import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.auth.User

class JsonResponse(val json: String) : Renderable {
    override fun getContentType(): ContentType = ContentType.Application.Json
    override fun getContent(user: User?, path: String): String = json

    companion object {
        inline fun <reified T> from(data: T): JsonResponse =
            JsonResponse(Json.encodeToString(data))
    }
}