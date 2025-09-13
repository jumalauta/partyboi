package party.jml.partyboi.messages

import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.db.many
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.system.AppResult
import java.util.*

class MessageRepository(val app: AppServices) {
    private val db = app.db

    suspend fun consumeUnreadMessages(userId: UUID): AppResult<List<Message>> = db.use {
        it.many(
            queryOf(
                """
           DELETE FROM message
           WHERE user_id = ?
           RETURNING *
        """.trimIndent(),
                userId
            ).map(Message.fromRow)
        )
    }

    suspend fun sendMessage(userId: UUID, type: MessageType, text: String): AppResult<Message> = db.use {
        it.one(
            queryOf(
                """
            INSERT INTO message(user_id, type, text) VALUES(?, ?, ?)
            RETURNING *
        """.trimIndent(),
                userId,
                type,
                text
            ).map(Message.fromRow)
        )
    }
}

data class Message(
    val id: UUID?,
    val userId: UUID?,
    val type: MessageType,
    val text: String,
) {
    companion object {
        val fromRow: (Row) -> Message = { row ->
            Message(
                id = row.uuid("id"),
                userId = row.uuidOrNull("user_id"),
                type = MessageType.valueOf(row.string("type")),
                text = row.string("text"),
            )
        }
    }
}

enum class MessageType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR;
}