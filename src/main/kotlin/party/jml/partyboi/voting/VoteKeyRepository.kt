package party.jml.partyboi.voting

import arrow.core.Either
import arrow.core.raise.either
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.DbBasicMappers.asString
import kotlin.random.Random

class VoteKeyRepository(val app: AppServices) {
    val db = app.db

    fun getUserVoteKeys(userId: Int): Either<AppError, List<VoteKey>> = db.use {
        it.many(queryOf("SELECT key FROM votekey WHERE appuser_id = ?", userId).map(asString))
    }.map { it.map(VoteKey.fromKey) }

    fun revokeUserVoteKeys(userId: Int) = db.use {
        it.exec(queryOf("DELETE FROM votekey WHERE appuser_id = ?", userId))
    }

    fun insertVoteKey(userId: Int?, voteKey: VoteKey, tx: TransactionalSession? = null) = db.use(tx) {
        it.exec(queryOf("INSERT INTO votekey (appuser_id, key) VALUES (?, ?)", userId, voteKey.toString()))
    }

    fun registerTicket(userId: Int, code: String): Either<AppError, Unit> = db.use {
        it.updateOne(
            queryOf(
                "UPDATE votekey SET appuser_id = ? WHERE appuser_id IS NULL AND key = ?",
                userId,
                VoteKey.ticket(code).toString()
            )
        )
    }

    fun createTickets(count: Int) = db.transaction { tx ->
        either {
            for (i in 1..count) {
                generateTicket(tx).bind()
            }
        }
    }

    private fun generateTicket(tx: TransactionalSession): Either<AppError, Unit> = either {
        val voteKey = VoteKey.ticket(generateTicketCode())
        val exists =
            tx.one(queryOf("SELECT true FROM votekey WHERE key = ?", voteKey.toString()).map(asBoolean)).bind()
        if (exists) {
            generateTicket(tx)
        } else {
            insertVoteKey(userId = null, voteKey, tx)
        }
    }

    private fun generateTicketCode(): String =
        (0..8).map { getRandomTicketChar() }.joinToString("")

    companion object {
        val TICKET_CHARS = "ABCDEFHJKLMNPRSTUVWXY2346789".toList()
        fun getRandomTicketChar() = TICKET_CHARS[Random.nextInt(0, TICKET_CHARS.size)]
    }
}

enum class VoteKeyType(val explain: (String) -> String) {
    USER({ "Created automatically on registration" }),
    IP({ "Created automatically for IP $it" }),
    TICKET({ "User entered a ticket: $it" }),
    MANUAL({ "Manually granted by $it" }),
    OTHER({ "Manually edited to database" }),
}

data class VoteKey(val keyType: VoteKeyType, val id: String? = null) {
    fun explain(): String = keyType.explain(id ?: "???")

    override fun toString(): String = listOfNotNull(keyType.name.lowercase(), id).joinToString(":")

    companion object {
        fun user(userId: Int) = VoteKey(VoteKeyType.USER, userId.toString())
        fun ipAddr(ipAddr: String) = VoteKey(VoteKeyType.IP, ipAddr)
        fun manual(grantedBy: String) = VoteKey(VoteKeyType.MANUAL, grantedBy)
        fun ticket(code: String) = VoteKey(VoteKeyType.TICKET, code.lowercase())

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
