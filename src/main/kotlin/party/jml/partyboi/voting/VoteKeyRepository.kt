@file:UseSerializers(
    OptionSerializer::class,
)

package party.jml.partyboi.voting

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.raise.either
import arrow.core.serialization.OptionSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.AppError
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.db.DbBasicMappers.asString
import party.jml.partyboi.replication.DataExport
import kotlin.random.Random

class VoteKeyRepository(val app: AppServices) : Logging() {
    val db = app.db

    fun getAllVoteKeys(): Either<AppError, List<VoteKeyRow>> = app.db.use {
        it.many(queryOf("SELECT * FROM votekey").map(VoteKeyRow.fromRow))
    }

    fun getVoteKeySet(keySet: String): Either<AppError, NonEmptyList<VoteKeyRow>> = app.db.use {
        it.atLeastOne(queryOf("SELECT * FROM votekey WHERE key_set = ?", keySet).map(VoteKeyRow.fromRow))
    }

    fun getUserVoteKeys(userId: Int): Either<AppError, List<VoteKey>> = db.use {
        it.many(queryOf("SELECT key FROM votekey WHERE appuser_id = ?", userId).map(asString))
    }.map { it.map(VoteKey.fromKey) }

    fun revokeUserVoteKeys(userId: Int) = db.use {
        it.exec(queryOf("DELETE FROM votekey WHERE appuser_id = ?", userId))
    }

    fun insertVoteKey(userId: Int?, voteKey: VoteKey, keySet: String?, tx: TransactionalSession? = null) = db.use(tx) {
        it.exec(
            queryOf(
                "INSERT INTO votekey (appuser_id, key, key_set) VALUES (?, ?, ?)",
                userId,
                voteKey.toString(),
                keySet
            )
        )
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

    fun createTickets(count: Int, keySet: String?) = db.transaction { tx ->
        either {
            for (i in 1..count) {
                generateTicket(tx, keySet).bind()
            }
        }
    }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.voteKeys.size} vote keys")
        data.voteKeys.map {
            tx.exec(
                queryOf(
                    "INSERT INTO votekey (key, appuser_id) VALUES (?, ?)",
                    it.key,
                    it.userId,
                )
            )
        }.bindAll()
    }

    private fun generateTicket(tx: TransactionalSession, keySet: String?): Either<AppError, Unit> = either {
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

    private fun generateTicketCode(): String =
        (0..8).map { getRandomTicketChar() }.joinToString("")

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
