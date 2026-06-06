package party.jml.partyboi.voting

import arrow.core.NonEmptyList
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.DbBasicMappers.asString
import party.jml.partyboi.messages.MessageType
import party.jml.partyboi.system.AppResult
import java.util.*
import kotlin.random.Random

class VoteKeyRepository(app: AppServices) : Service(app) {
    val db = app.db

    suspend fun getAllVoteKeys(): AppResult<List<VoteKeyRow>> = app.db.use {
        many(queryOf("SELECT * FROM votekey").map(VoteKeyRow.fromRow))
    }

    suspend fun getVoteKeySet(keySet: String): AppResult<NonEmptyList<VoteKeyRow>> = app.db.use {
        atLeastOne(queryOf("SELECT * FROM votekey WHERE key_set = ?", keySet).map(VoteKeyRow.fromRow))
    }

    suspend fun getUserVoteKeys(userId: UUID): AppResult<List<VoteKey>> = db.use {
        many(queryOf("SELECT key FROM votekey WHERE user_id = ?", userId).map(asString))
    }.map { it.map(VoteKey.fromKey) }

    suspend fun revokeUserVoteKeys(userId: UUID) = db.use {
        exec(queryOf("DELETE FROM votekey WHERE user_id = ?", userId))
    }

    suspend fun insertVoteKey(userId: UUID?, voteKey: VoteKey, keySet: String?, tx: TransactionalSession? = null) =
        db.use(tx) {
            exec(
                queryOf(
                    "INSERT INTO votekey (user_id, key, key_set) VALUES (?, ?, ?)",
                    userId,
                    voteKey.toString(),
                    keySet
                )
            )
        }.onRight {
            userId?.let { notifyUserOfVotingRights(it) }
        }

    suspend fun registerTicket(userId: UUID, code: String): AppResult<Unit> =
        db.use {
            updateOne(
                queryOf(
                    "UPDATE votekey SET user_id = ? WHERE user_id IS NULL AND key = ?",
                    userId,
                    VoteKey.ticket(code).toString()
                )
            )
        }.onRight {
            notifyUserOfVotingRights(userId)
        }

    suspend fun createTickets(count: Int, keySet: String?) = db.transaction {
        either {
            (1..count).forEach { i ->
                generateTicket(this@transaction, keySet).bind()
            }
        }
    }

    suspend fun grantVotingRightsByEmail() = db.use {
        many(
            queryOf(
                """
                INSERT INTO votekey (key, user_id) (
                	SELECT
                		('email:' || email) AS key,
                		id AS user_id
                	FROM appuser
                	WHERE
                		id NOT IN (
                            SELECT DISTINCT user_id
                			FROM votekey
                			WHERE user_id IS NOT NULL
                        )
                        AND EMAIL IN (
                            SELECT jsonb_array_elements_text(value) AS emails
                            FROM property
                            WHERE key = 'SettingsService.voteKeyEmailList'
                        )
                )
                RETURNING user_id
            """.trimIndent()
            ).map({ it.uuid("user_id") })
        ).map { userIds ->
            userIds.forEach { userId -> notifyUserOfVotingRights(userId) }
        }
    }

    private suspend fun generateTicket(tx: TransactionalSession, keySet: String?, remainingAttempts: Int = 100): AppResult<Unit> = either {
        require(remainingAttempts > 0) { "Failed to generate unique ticket code after 100 attempts" }
        val voteKey = VoteKey.ticket(generateTicketCode())
        val exists = tx
            .one(queryOf("SELECT true FROM votekey WHERE key = ?", voteKey.toString()).map(asBoolean))
            .isRight()
        if (exists) {
            generateTicket(tx, keySet, remainingAttempts - 1).bind()
        } else {
            insertVoteKey(userId = null, voteKey, keySet, tx).bind()
        }
    }

    suspend fun deleteAll() = db.use {
        exec(queryOf("DELETE FROM votekey"))
    }

    private fun generateTicketCode(): String =
        (0..7).map { getRandomTicketChar() }.joinToString("")

    private suspend fun notifyUserOfVotingRights(userId: UUID) {
        app.messages.sendMessage(
            userId,
            MessageType.INFO,
            "You have been granted rights to vote."
        )
    }

    companion object {
        val TICKET_CHARS = "ABCDEFHJKLMNPRSTUVWXY2346789".toList()
        fun getRandomTicketChar() = TICKET_CHARS[Random.nextInt(0, TICKET_CHARS.size)]
    }
}

enum class VoteKeyType(val explain: (String?) -> String) {
    USER({ "Created automatically on registration" }),
    IP({ if (it != null) "Created automatically for IP $it" else "Created automatically per IP" }),
    TICKET({ if (it != null) "User entered a ticket: $it" else "Vote key ticket" }),
    MANUAL({ "Manually granted by admin" }),
    EMAIL({ "Registration email ${it ?: ""}" }),
    OTHER({ "Manually edited to database" }),
}

@Serializable
data class VoteKey(val keyType: VoteKeyType, val id: String? = null) {
    fun explain(): String = keyType.explain(id ?: "???")

    override fun toString(): String = listOfNotNull(keyType.name.lowercase(), id).joinToString(":")

    companion object {
        fun user(userId: UUID) = VoteKey(VoteKeyType.USER, userId.toString())
        fun ipAddr(ipAddr: String) = VoteKey(VoteKeyType.IP, ipAddr)
        fun manual(userId: UUID) = VoteKey(VoteKeyType.MANUAL, userId.toString())
        fun ticket(code: String) = VoteKey(VoteKeyType.TICKET, code.lowercase())
        fun email(email: String) = VoteKey(VoteKeyType.EMAIL, email)

        val fromKey: (String) -> VoteKey = { key ->
            val tokens = key.split(":")
            val keyType = runCatching { VoteKeyType.valueOf(tokens.first().uppercase()) }
                .getOrDefault(VoteKeyType.OTHER)
            val id = tokens.getOrNull(1)
            VoteKey(keyType, id)
        }
    }
}

@Serializable
data class VoteKeyRow(
    val key: VoteKey,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID?,
    val set: String?,
) {
    companion object {
        val fromRow: (Row) -> VoteKeyRow = { row ->
            VoteKeyRow(
                key = VoteKey.fromKey(row.string("key")),
                userId = row.uuidOrNull("user_id"),
                set = row.stringOrNull("key_set"),
            )
        }
    }
}
