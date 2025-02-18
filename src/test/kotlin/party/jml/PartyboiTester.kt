package party.jml

import arrow.core.raise.Raise
import arrow.core.raise.either
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import it.skrape.core.htmlDocument
import it.skrape.matchers.toBe
import it.skrape.selects.Doc
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.services
import kotlin.test.assertEquals

interface PartyboiTester {
    fun test(block: suspend ApplicationTestBuilder.(TestHtmlClient) -> Unit) {
        testApplication {
            environment {
                config = ApplicationConfig("tests.yaml")
            }
            block(htmlClient())
        }
    }
}

fun ApplicationTestBuilder.htmlClient(): TestHtmlClient {
    val client = createClient {
        install(HttpCookies)
    }
    return TestHtmlClient(client)
}

class TestHtmlClient(val client: HttpClient) {
    suspend fun get(
        urlString: String,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        block: (Doc.() -> Unit)? = null
    ) {
        client.get(urlString).apply {
            assertEquals(expectedStatus, status)
            htmlDocument(bodyAsText()) {
                if (block != null) block()
            }
        }
    }

    suspend fun post(urlString: String, formData: List<PartData>, block: Doc.(Headers) -> Unit) {
        client.submitFormWithBinaryData(
            url = urlString,
            formData = formData
        ).apply {
            htmlDocument(bodyAsText()) {
                block(headers)
            }
        }
    }
}

fun Headers.redirectsTo(urlString: String) {
    this["Location"].toBe(urlString)
}

fun ApplicationTestBuilder.services(block: Raise<AppError>.(AppServices) -> Unit) {
    application {
        either {
            block(services())
        }.onLeft {
            throw it.throwable ?: RuntimeException(it.message)
        }
    }
}