package party.jml.partyboi.syncharness

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.Closeable

/**
 * Cookie storage that strips the Secure flag on every stored cookie. The partyboi server
 * marks its session cookie Secure (good for prod), but the harness talks to the appservers
 * over plain HTTP on localhost, so Ktor's default storage would refuse to send the cookie
 * back and every authenticated request would be redirected to /login.
 */
private class InsecureCookiesStorage : CookiesStorage {
    private val delegate = AcceptAllCookiesStorage()
    override suspend fun get(requestUrl: Url): List<Cookie> = delegate.get(requestUrl)
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        delegate.addCookie(requestUrl, cookie.copy(secure = false))
    }
    override fun close() = delegate.close()
}

/**
 * HTTP client bound to a single partyboi instance (master or remote). Each instance
 * gets its own client so session cookies don't leak across sides.
 */
class InstanceClient(
    val label: String,
    val baseUrl: String,
) : Closeable {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @PublishedApi
    internal val client = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(HttpCookies) {
            storage = InsecureCookiesStorage()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10 * 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 10 * 60_000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun login(name: String, password: String) {
        postMultipart("/login", listOf("name" to name, "password" to password)).expectOk()
    }

    /**
     * Complete the first-run setup wizard. A fresh instance redirects every admin request to
     * /wizard until the wizard has been submitted, so the harness must run it once right after
     * logging in as admin. Sends the GeneralSettings step with harmless defaults.
     */
    suspend fun completeWizard() {
        postMultipart(
            "/wizard",
            listOf(
                "resultsFileHeader" to "",
                "colorScheme" to "Blue",
                "timeZone" to "UTC",
            ),
        ).expectOk()
    }

    suspend fun register(name: String, password: String, email: String = "") {
        val resp = postMultipart(
            "/register",
            listOf(
                "name" to name,
                "password" to password,
                "password2" to password,
                "email" to email,
                "isUpdate" to "false",
            ),
        )
        resp.expectOk()
    }

    suspend fun postMultipart(path: String, fields: List<Pair<String, String>>): HttpResponse =
        client.submitFormWithBinaryData(
            url = baseUrl + path,
            formData = formData {
                fields.forEach { (k, v) -> append(k, v) }
            },
        )

    suspend fun postMultipartWithFile(
        path: String,
        fields: List<Pair<String, String>>,
        fileField: String,
        fileName: String,
        fileBytes: ByteArray,
        fileContentType: String = "application/octet-stream",
    ): HttpResponse =
        client.submitFormWithBinaryData(
            url = baseUrl + path,
            formData = formData {
                fields.forEach { (k, v) -> append(k, v) }
                append(fileField, fileBytes, Headers.build {
                    append(HttpHeaders.ContentType, fileContentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            },
        )

    suspend fun put(path: String): HttpResponse = client.put(baseUrl + path)

    suspend fun get(path: String): HttpResponse = client.get(baseUrl + path)

    suspend fun get(path: String, block: HttpRequestBuilder.() -> Unit): HttpResponse =
        client.get(baseUrl + path, block)

    suspend fun getHtml(path: String): String = client.get(baseUrl + path).bodyAsText()

    suspend inline fun <reified T> getJsonWithToken(path: String, token: String): T =
        client.get(baseUrl + path) {
            accept(ContentType.Application.Json)
            bearerAuth(token)
        }.body()

    suspend fun getHealth(): Boolean = try {
        client.get("$baseUrl/health").status == HttpStatusCode.OK
    } catch (_: Exception) {
        false
    }

    /**
     * Generate a new sync token by hitting /sync/new-token and parsing the resulting HTML.
     * The page renders the token inside a `<td><small>{token}</small></td>` whose sibling
     * `<th>` says "Token:".
     */
    suspend fun generateSyncToken(): String {
        val html = client.post("$baseUrl/sync/new-token").bodyAsText()
        val doc = Jsoup.parse(html)
        val row = doc.select("tr").firstOrNull { it.selectFirst("th")?.text()?.startsWith("Token") == true }
            ?: error("[$label] /sync/new-token response did not contain a Token row. Body:\n$html")
        return row.selectFirst("td small")?.text()
            ?: error("[$label] Could not extract token text from /sync/new-token response")
    }

    override fun close() {
        client.close()
    }
}

fun HttpResponse.expectOk(): HttpResponse {
    val s = status
    if (s.value !in 200..399) {
        error("Request to $request failed with $s")
    }
    return this
}
