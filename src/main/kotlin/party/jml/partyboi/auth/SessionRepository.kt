package party.jml.partyboi.auth

import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import party.jml.partyboi.db.DatabasePool
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.db.updateAny
import party.jml.partyboi.system.AppResult
import java.util.*

class SessionRepository(private val db: DatabasePool) : SessionStorage {
    override suspend fun invalidate(id: String) {
        db.useUnsafe { execute(queryOf("DELETE FROM session WHERE id = ?", id)) }
    }

    override suspend fun read(id: String): String {
        return db.useUnsafe {
            // Reject sessions whose server-side lifetime has passed: the cookie max-age is only a
            // browser hint, so expiry must be enforced here for a stolen or replayed cookie.
            val query = queryOf(
                "SELECT value FROM session WHERE id = ? AND (expires_at IS NULL OR expires_at > now())",
                id
            )
                .map { it.string(1) }
                .asSingle
            run(query) ?: throw NoSuchElementException("Session $id not found")
        }
    }

    override suspend fun write(id: String, value: String) {
        db.useUnsafe {
            // Record the owning user and a fresh expiry on every write (login and session reload), so
            // a user's sessions can be invalidated wholesale and so each session ages out server-side.
            execute(
                queryOf(
                    """
                    INSERT INTO session (id, value, user_id, expires_at)
                    VALUES (?, ?, ?, now() + interval '$SESSION_TTL')
                    ON CONFLICT (id) DO UPDATE SET
                        value = EXCLUDED.value,
                        user_id = EXCLUDED.user_id,
                        expires_at = EXCLUDED.expires_at""",
                    id, value, userIdOf(value)
                )
            )
        }
    }

    /** Invalidate every session belonging to a user (on password change, demotion, or deletion). */
    suspend fun invalidateUserSessions(userId: UUID): AppResult<Int> =
        db.use { updateAny(queryOf("DELETE FROM session WHERE user_id = ?", userId)) }

    /** Physically remove sessions whose lifetime has passed (read already ignores them). */
    suspend fun purgeExpired(): AppResult<Int> =
        db.use { updateAny(queryOf("DELETE FROM session WHERE expires_at IS NOT NULL AND expires_at <= now()")) }

    // The session value is the kotlinx-serialized User (JSON), whose "id" field is the user's UUID.
    // Parsing defensively: an unexpected shape simply yields a null owner rather than failing the
    // write — the session still works, it just can't be bulk-invalidated by user.
    private fun userIdOf(value: String): UUID? =
        runCatching {
            value
                .let(Json::parseToJsonElement)
                .jsonObject["id"]
                ?.jsonPrimitive?.content
                ?.let(UUID::fromString)
        }.getOrNull()

    companion object {
        // Keep in sync with the session cookie's max-age in Authentication.kt.
        private const val SESSION_TTL = "7 days"
    }
}
