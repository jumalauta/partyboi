package party.jml.partyboi.voting

import arrow.core.Option
import arrow.core.toOption
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.db.DatabasePool
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.system.AppResult
import java.util.*

class VoteRepository(app: AppServices) : Service(app) {
    private val db: DatabasePool = app.db

    suspend fun castVote(userId: UUID, entryId: UUID, points: Int): AppResult<Unit> = db.use {
        it.exec(
            queryOf(
                """
                INSERT INTO vote (user_id, entry_id, points)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, entry_id) DO UPDATE SET
                    points = EXCLUDED.points
            """,
                userId,
                entryId,
                points,
            )
        )
    }

    suspend fun getUserVotes(userId: UUID): AppResult<List<VoteRow>> = db.use {
        it.many(
            queryOf(
                """
                SELECT *
                FROM vote
                WHERE vote.user_id = ?
            """.trimIndent(),
                userId,
            ).map(VoteRow.fromRow)
        )
    }

    suspend fun getAllVotes(): AppResult<List<VoteRow>> = db.use {
        it.many(
            queryOf(
                """
            SELECT
            	vote.user_id,
            	entry_id,
            	compo_id,
            	points
            FROM vote
            JOIN entry ON vote.entry_id = entry.id
        """.trimIndent()
            ).map(VoteRow.fromRow)
        )
    }
    
    suspend fun deleteAll() = db.use {
        it.exec(queryOf("DELETE FROM vote"))
    }
}

data class CompoResult(
    val compoId: UUID,
    val compoName: String,
    val points: Int,
    val entryId: UUID,
    val title: String,
    val author: String,
    val info: Option<String>,
    val downloadLink: String? = null,
) {
    companion object {
        val fromRow: (Row) -> CompoResult = { row ->
            CompoResult(
                compoId = row.uuid("compo_id"),
                compoName = row.string("compo_name"),
                points = row.int("points"),
                entryId = row.uuid("entry_id"),
                title = row.string("title"),
                author = row.string("author"),
                info = row.stringOrNull("screen_comment")?.nonEmptyString().toOption(),
                downloadLink = null,
            )
        }

        fun groupResults(results: List<CompoResult>): Map<CompoGroup, List<GroupedCompoResult>> = results
            .groupBy { CompoGroup(it.compoId, it.compoName) }
            .mapValues { (_, compoResults) ->
                val grouped = compoResults
                    .sortedBy { -it.points }
                    .groupBy { it.points }
                    .map { it.value }
                val places = grouped.scan(1) { place, rs -> place + rs.size }
                places.zip(grouped).map { (place, rs) -> GroupedCompoResult(place, rs) }
            }

        data class CompoGroup(
            val id: UUID,
            val name: String,
        )

        data class GroupedCompoResult(
            val place: Int,
            val results: List<CompoResult>,
        )
    }
}

data class VoteRow(
    val userId: UUID,
    val entryId: UUID,
    val compoId: UUID,
    val points: Int
) {
    companion object {
        val fromRow: (Row) -> VoteRow = { row ->
            VoteRow(
                userId = row.uuid("user_id"),
                entryId = row.uuid("entry_id"),
                compoId = row.uuid("compo_id"),
                points = row.int("points"),
            )
        }
    }
}
