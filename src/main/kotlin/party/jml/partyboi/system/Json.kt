package party.jml.partyboi.system

import arrow.core.left
import arrow.core.right
import kotlinx.serialization.json.Json
import party.jml.partyboi.data.InternalServerError

inline fun <reified T> Json.encodeToStringSafe(value: T): AppResult<String> =
    try {
        encodeToString(value).right()
    } catch (e: Exception) {
        InternalServerError(e).left()
    }