package party.jml.partyboi.templates

import io.ktor.server.routing.*
import kotlinx.coroutines.flow.Flow

fun RoutingContext.longPollingHeaders() {
    val headers = call.response.headers
    headers.append("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
    headers.append("Pragma", "no-cache")
    headers.append("Expires", "0")
}


suspend fun <T> RoutingContext.longPolling(flow: Flow<T>, handle: suspend (T) -> Unit) {
    longPollingHeaders()
    flow.collect { data -> handle(data) }
}