package party.jml

import arrow.core.raise.either
import arrow.core.right
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.ktor.util.*
import it.skrape.core.htmlDocument
import it.skrape.matchers.toBe
import it.skrape.selects.Doc
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.User
import party.jml.partyboi.auth.UserCredentials
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.services
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.system.AppResult
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

interface PartyboiTester {
    fun test(block: suspend ApplicationTestBuilder.(TestHtmlClient) -> Unit) {
        testApplication {
            environment { config = ApplicationConfig("tests.yaml") }
            val client = createClient { install(HttpCookies) }
            block(TestHtmlClient(client))
        }
    }

    fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(bytes)).toString(16).padStart(32, '0')
    }

    suspend fun addTestUser(app: AppServices, name: String = "user", password: String = "password"): AppResult<User> =
        app.users.addUser(UserCredentials(name, password, password, ""), "0.0.0.0")

    suspend fun addTestAdmin(app: AppServices, name: String = "admin", password: String = "password"): AppResult<User> =
        either {
            val user = addTestUser(app, name, password).bind()
            app.users.makeAdmin(user.id, true)
            user.copy(isAdmin = true)
        }
}

class TestHtmlClient(val client: HttpClient) {
    suspend fun <T> get(
        urlString: String,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        block: (Doc.() -> T)
    ): T {
        client.get(urlString).apply {
            assertEquals(expectedStatus, status)
            return htmlDocument(bodyAsText()) {
                relaxed = true
                block()
            }
        }
    }

    suspend fun <T> getBinary(
        urlString: String,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        block: (ByteArray) -> T
    ) {
        client.get(urlString).apply {
            assertEquals(expectedStatus, status)
            block(bodyAsChannel().toByteArray())
        }
    }

    suspend inline fun <reified A, T> getJson(
        urlString: String,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        block: (A) -> T
    ): T {
        var result: T? = null
        client.get(urlString).apply {
            assertEquals(expectedStatus, status)
            val response = Json.decodeFromString<A>(bodyAsText())
            result = block(response)
        }
        return result!!
    }

    suspend fun <T> post(
        urlString: String,
        formData: List<PartData>,
        block: (Doc.(Headers) -> T)
    ): T {
        client.submitFormWithBinaryData(
            url = urlString,
            formData = formData
        ).apply {
            return htmlDocument(bodyAsText()) {
                relaxed = true
                block(headers)
            }
        }
    }

    suspend inline fun <reified T : Validateable<T>, A> post(
        urlString: String,
        obj: Validateable<T>,
        noinline block: (Doc.(Headers) -> A)
    ): A {
        val map = T::class.memberProperties.associate { it.name to it.getter.call(obj) }
        val data = formData {
            map.forEach {
                when (val value = it.value) {
                    is FileUpload -> append(it.key, value.toByteArray(), headers {
                        append("Content-Type", "application/octet-stream")
                        append("Content-Disposition", "form-data; name=\"file\"; filename=\"${value.name}\"")
                    })

                    else -> append(it.key, value.toString())
                }
            }
        }
        return post(urlString, data, block)
    }

    suspend inline fun buttonClick(urlString: String) {
        client.put(urlString).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    suspend inline fun buttonClickFails(urlString: String) {
        client.put(urlString).apply {
            assertNotEquals(HttpStatusCode.OK, status)
        }
    }

    suspend fun login(username: String = "user", password: String = "password") {
        post("/register", formData {
            append("name", username)
            append("password", password)
            append("password2", password)
            append("isUpdate", "")
        }) {}

        post("/login", formData {
            append("name", username)
            append("password", password)
        }) {}
    }
}

fun Headers.redirectsTo(urlString: String) {
    this["Location"]?.trim().toBe(urlString.trim())
}

fun ApplicationTestBuilder.setupServices() {
    Unit.right()
}

fun <T> ApplicationTestBuilder.setupServices(setupForTest: suspend AppServices.() -> AppResult<T>) {
    application {
        runBlocking {
            val app = services()
            either {
                app.settings.automaticVoteKeys.set(AutomaticVoteKeys.DISABLED)
                app.events.deleteAll().bind()
                app.screen.deleteAll().bind()
                app.votes.deleteAll().bind()
                app.voteKeys.deleteAll().bind()
                app.entries.deleteAll().bind()
                app.compos.deleteAll().bind()
                app.compos.generalRules.set(GeneralRules("")).bind()
                app.users.deleteAll().bind()
                app.email.reset()
                
                runBlocking {
                    app.setupForTest().bind()
                }
            }.onLeft {
                throw it.throwable ?: RuntimeException(it.message)
            }
        }
    }
}