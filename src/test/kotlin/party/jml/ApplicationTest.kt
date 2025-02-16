package party.jml

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import it.skrape.core.*
import it.skrape.matchers.*
import it.skrape.selects.html5.*
import it.skrape.selects.text
import party.jml.partyboi.services
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testFrontPage() = testPartyboi { client ->
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            htmlDocument(bodyAsText()) {
                main {
                    header {
                        findFirst {
                            li {
                                findFirst { text.toBe("Partyboi (test)") }
                                findSecond { text.toBe("Login") }
                                findThird { text.toBe("Register") }
                            }
                        }
                    }
                    aside {
                        findAll(".page-nav li") { text.toBe("Info Compos Results") }
                    }
                }
            }
        }
    }

    @Test
    fun testRegistration() = testPartyboi { client ->
        val userName = "foobar"
        application { services().users.deleteUserByName(userName) }

        // User cannot see the entry page before registration
        client.get("/entries").apply {
            assertEquals(HttpStatusCode.OK, status)
            htmlDocument(bodyAsText()) {
                findFirst("article header") { text.toBe("Login") }
            }
        }

        // Check that registration page loads
        client.get("/register").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        // Register with invalid data -> shows errors
        client.submitFormWithBinaryData(
            url = "/register",
            formData = formData {
                append("name", "")
                append("password", "hunter2")
                append("password2", "password")
            }
        ).apply {
            // assertEquals(HttpStatusCode.BadRequest, status)
            htmlDocument(bodyAsText()) {
                findFirst(".error") { text.toBe("Value cannot be empty") }
                findSecond(".error") { text.toBe("Minimum length is 8 characters") }
                findThird(".error") { text.toBe("Value is not equal") }
            }
        }

        // Register with good data -> redirects to entry page
        client.submitFormWithBinaryData(
            url = "/register",
            formData = formData {
                append("name", userName)
                append("password", "password")
                append("password2", "password")
            }
        ).apply {
            assertEquals(HttpStatusCode.Found, status)
            headers["Location"].toBe("/entries")
        }

        // User can see the entry page now
        client.get("/entries").apply {
            assertEquals(HttpStatusCode.OK, status)
            htmlDocument(bodyAsText()) {
                findFirst("h1") { text.toBe("Entries") }
            }
        }

        // Register with same username again -> shows an error
        client.submitFormWithBinaryData(
            url = "/register",
            formData = formData {
                append("name", userName)
                append("password", "password")
                append("password2", "password")
            }
        ).apply {
            // assertEquals(HttpStatusCode.BadRequest, status)
            htmlDocument(bodyAsText()) {
                findFirst(".error") { text.toBe("The user name has already been registered") }
            }
        }
    }
}

fun testPartyboi(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
    testApplication {
        environment {
            config = ApplicationConfig("tests.yaml")
        }
        val client = createClient {
            install(HttpCookies)
        }
        block(client)
    }
}