package party.jml

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import it.skrape.core.*
import it.skrape.matchers.*
import it.skrape.selects.html5.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        environment {
            config = ApplicationConfig("tests.yaml")
        }
        val client = createClient {
            install(HttpCookies)
        }

        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            htmlDocument(bodyAsText()) {
                main {
                    header {
                        li {
                            findFirst {
                                text.toBe("Partyboi (test)")
                            }
                        }
                    }
                }
            }
        }
    }
}
