package party.jml

import arrow.core.raise.either
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.queryOf
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionInvalidationTest : PartyboiTester {

    // Regression: the admin flag was trusted from the stored session and only refreshed opportunistically,
    // so a demoted admin kept admin access — and could re-promote themselves — until some handler happened
    // to reload their session. Revoking admin now drops the user's sessions, forcing a fresh login that
    // reads current privileges from the database.
    @Test
    fun `demoting an admin invalidates their session and blocks re-promotion`() = test {
        var victimId: UUID = UUID(0, 0)
        setupServices {
            val app = this
            either {
                addTestAdmin(app, "boss").bind()
                victimId = addTestAdmin(app, "victim").bind().id
            }
        }

        // The victim is an admin and can reach an admin-only page.
        it.login("victim", "password")
        it.get("/admin/users", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Users") }
        }

        // Another admin demotes the victim.
        val boss = TestHtmlClient(createClient { install(HttpCookies) })
        boss.login("boss", "password")
        boss.buttonClick("/admin/users/$victimId/admin/false")

        // The victim's session is now gone: the admin page falls back to the login form...
        it.get("/admin/users", HttpStatusCode.OK) {
            findFirst("article header") { text.toBe("Login") }
        }
        // ...and the victim can no longer call the admin API to re-grant themselves admin.
        it.buttonClickFails("/admin/users/$victimId/admin/true")
    }

    @Test
    fun `read rejects and purge removes expired sessions, and invalidation drops a user's sessions`() = test {
        setupServices {
            val app = this
            either {
                val userId = UUID.randomUUID()
                val value = sessionBlob(userId)

                // A live session reads back its stored value.
                app.sessions.write("live", value)
                assertEquals(value, app.sessions.read("live"))

                // A session past its expiry is rejected by read (the cookie max-age is only a browser hint)...
                app.sessions.write("stale", value)
                app.db.use {
                    exec(queryOf("UPDATE session SET expires_at = now() - interval '1 hour' WHERE id = 'stale'"))
                }.bind()
                assertFailsWith<NoSuchElementException> { app.sessions.read("stale") }

                // ...and is physically removed by the purge, while the live session survives.
                assertTrue(app.sessions.purgeExpired().bind() >= 1)
                assertFailsWith<NoSuchElementException> { app.sessions.read("stale") }
                assertEquals(value, app.sessions.read("live"))

                // Invalidating by user drops that user's sessions.
                app.sessions.invalidateUserSessions(userId).bind()
                assertFailsWith<NoSuchElementException> { app.sessions.read("live") }
            }
        }
        it.get("/") {}
    }

    private fun sessionBlob(userId: UUID): String =
        """{"id":"$userId","name":"x","hashedPassword":"h","isAdmin":false,"votingEnabled":false,"email":null,"emailVerified":false}"""
}
