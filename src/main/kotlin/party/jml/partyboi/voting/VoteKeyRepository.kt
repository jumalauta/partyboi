package party.jml.partyboi.voting

import arrow.core.Either
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.db.DbBasicMappers.asString
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.queryOf

class VoteKeyRepository(val app: AppServices) {
    val db = app.db

    fun getUserVoteKeys(userId: Int): Either<AppError, List<VoteKey>> = db.use {
        it.many(queryOf("SELECT key FROM votekey WHERE appuser_id = ?", userId).map(asString))
    }.map { it.map(VoteKey.fromKey) }

    fun revokeUserVoteKeys(userId: Int) = db.use {
        it.exec(queryOf("DELETE FROM votekey WHERE appuser_id = ?", userId))
    }

    fun registerVoteKey(userId: Int, voteKey: VoteKey) = db.use {
        it.exec(queryOf("INSERT INTO votekey (appuser_id, key) VALUES (?, ?)", userId, voteKey.toString()))
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
