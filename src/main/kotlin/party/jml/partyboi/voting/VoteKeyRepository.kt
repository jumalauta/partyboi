@file:UseSerializers(
    OptionSerializer::class,
)

package party.jml.partyboi.voting

import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.raise.either
import arrow.core.serialization.OptionSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.DbBasicMappers.asInt
import party.jml.partyboi.db.DbBasicMappers.asString
import party.jml.partyboi.messages.MessageType
import party.jml.partyboi.system.AppResult
import kotlin.random.Random

class VoteKeyRepository(app: AppServices) : Service(app) {
    val db = app.db

    suspend fun getAllVoteKeys(): AppResult<List<VoteKeyRow>> = app.db.use {
        it.many(queryOf("SELECT * FROM votekey").map(VoteKeyRow.fromRow))
    }

    suspend fun getVoteKeySet(keySet: String): AppResult<NonEmptyList<VoteKeyRow>> = app.db.use {
        it.atLeastOne(queryOf("SELECT * FROM votekey WHERE key_set = ?", keySet).map(VoteKeyRow.fromRow))
    }

    suspend fun getUserVoteKeys(userId: Int): AppResult<List<VoteKey>> = db.use {
        it.many(queryOf("SELECT key FROM votekey WHERE appuser_id = ?", userId).map(asString))
    }.map { it.map(VoteKey.fromKey) }

    suspend fun revokeUserVoteKeys(userId: Int) = db.use {
        it.exec(queryOf("DELETE FROM votekey WHERE appuser_id = ?", userId))
    }

    suspend fun insertVoteKey(userId: Int?, voteKey: VoteKey, keySet: String?, tx: TransactionalSession? = null) =
        db.use(tx) {
            it.exec(
                queryOf(
                    "INSERT INTO votekey (appuser_id, key, key_set) VALUES (?, ?, ?)",
                    userId,
                    voteKey.toString(),
                    keySet
                )
            )
        }.onRight {
            userId?.let { notifyUserOfVotingRights(it) }
        }

    suspend fun registerTicket(userId: Int, code: String): AppResult<Unit> =
        db.use {
            it.updateOne(
                queryOf(
                    "UPDATE votekey SET appuser_id = ? WHERE appuser_id IS NULL AND key = ?",
                    userId,
                    VoteKey.ticket(code).toString()
                )
            )
        }.onRight {
            notifyUserOfVotingRights(userId)
        }

    suspend fun createTickets(count: Int, keySet: String?) = db.transaction { tx ->
        either {
            (1..count).forEach { i ->
                generateTicket(tx, keySet).bind()
            }
        }
    }

    suspend fun grantVotingRightsByEmail() = db.use {
        it.many(
            queryOf(
                """
                INSERT INTO votekey (key, appuser_id) (
                	SELECT
                		('email:' || email) AS key,
                		id AS appuser_id
                	FROM appuser
                	WHERE
                		id NOT IN (
                            SELECT DISTINCT appuser_id
                			FROM votekey
                			WHERE appuser_id IS NOT NULL
                        )
                        AND EMAIL IN (
                            SELECT jsonb_array_elements_text(value) AS emails
                            FROM property
                            WHERE key = 'SettingsService.voteKeyEmailList'
                        )
                )
                RETURNING appuser_id
            """.trimIndent()
            ).map(asInt)
        ).map { userIds ->
            userIds.forEach { userId -> notifyUserOfVotingRights(userId) }
        }
    }

    private suspend fun generateTicket(tx: TransactionalSession, keySet: String?): AppResult<Unit> = either {
        val voteKey = VoteKey.ticket(generateTicketCode())
        val exists = tx
            .one(queryOf("SELECT true FROM votekey WHERE key = ?", voteKey.toString()).map(asBoolean))
            .isRight()
        if (exists) {
            generateTicket(tx, keySet)
        } else {
            insertVoteKey(userId = null, voteKey, keySet, tx)
        }
    }

    suspend fun deleteAll() = db.use {
        it.exec(queryOf("DELETE FROM votekey"))
    }

    private fun generateTicketCode(): String =
        (0..7).map { getRandomTicketChar() }.joinToString("")

    private suspend fun notifyUserOfVotingRights(userId: Int) {
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
        fun user(userId: Int) = VoteKey(VoteKeyType.USER, userId.toString())
        fun ipAddr(ipAddr: String) = VoteKey(VoteKeyType.IP, ipAddr)
        fun manual(userId: Int) = VoteKey(VoteKeyType.MANUAL, userId.toString())
        fun ticket(code: String) = VoteKey(VoteKeyType.TICKET, code.lowercase())
        fun email(email: String) = VoteKey(VoteKeyType.EMAIL, email)

        val fromKey: (String) -> VoteKey = { key ->
            val tokens = key.split(":")
            val keyType = try {
                VoteKeyType.valueOf(tokens.first().uppercase())
            } catch (_: Throwable) {
                VoteKeyType.OTHER
            }
            val id = tokens.getOrNull(1)
            VoteKey(keyType, id)
        }
    }
}

@Serializable
data class VoteKeyRow(
    val key: VoteKey,
    val userId: Option<Int>,
    val set: String?,
) {
    companion object {
        val fromRow: (Row) -> VoteKeyRow = { row ->
            VoteKeyRow(
                key = VoteKey.fromKey(row.string("key")),
                userId = Option.fromNullable(row.intOrNull("appuser_id")),
                set = row.stringOrNull("key_set"),
            )
        }
    }
}
