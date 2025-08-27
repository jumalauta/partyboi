package party.jml.partyboi.templates

import io.ktor.http.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.Flow

fun RoutingContext.longPollingHeaders() {
    val headers = call.response.headers
    headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
    headers.append(HttpHeaders.Pragma, "no-cache")
    headers.append(HttpHeaders.Expires, "0")
    headers.append(HttpHeaders.Connection, "keep-alive")
}


suspend fun <T> RoutingContext.longPolling(flow: Flow<T>, handle: suspend (T) -> Unit) {
    longPollingHeaders()
    flow.collect { data -> handle(data) }
}