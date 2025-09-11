package party.jml.partyboi.voting

import arrow.core.Option
import arrow.core.raise.either
import arrow.core.toOption
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.db.DatabasePool
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.system.AppResult

class VoteRepository(app: AppServices) : Service(app) {
    private val db: DatabasePool = app.db

    suspend fun castVote(userId: Int, entryId: Int, points: Int): AppResult<Unit> = db.use {
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

    suspend fun getUserVotes(userId: Int): AppResult<List<VoteRow>> = db.use {
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
        it.many(queryOf("SELECT * FROM vote").map(VoteRow.fromRow))
    }

    suspend fun getResults(onlyPublic: Boolean): AppResult<List<CompoResult>> = db.use {
        it.many(
            queryOf(
                """
            SELECT
                compo_id,
                compo.name as compo_name,
                coalesce(sum(points), 0) AS points,
                entry.id AS entry_id,
                title,
                author,
                screen_comment
            FROM entry
            LEFT JOIN vote ON entry.id = vote.entry_id
            JOIN compo ON compo.id = entry.compo_id
            WHERE qualified
            ${if (onlyPublic) "AND public_results" else ""}
            GROUP BY compo_id, compo.name, entry.id, title, author
            ORDER BY compo_id, points DESC, entry_id
        """.trimIndent()
            ).map(CompoResult.fromRow)
        )
    }

    suspend fun deleteAll() = db.use {
        it.exec(queryOf("DELETE FROM vote"))
    }
}

data class CompoResult(
    val compoId: Int,
    val compoName: String,
    val points: Int,
    val entryId: Int,
    val title: String,
    val author: String,
    val info: Option<String>,
    val downloadLink: String?,
) {
    companion object {
        val fromRow: (Row) -> CompoResult = { row ->
            CompoResult(
                compoId = row.int("compo_id"),
                compoName = row.string("compo_name"),
                points = row.int("points"),
                entryId = row.int("entry_id"),
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
            val id: Int,
            val name: String,
        )

        data class GroupedCompoResult(
            val place: Int,
            val results: List<CompoResult>,
        )
    }
}

@Serializable
data class VoteRow(
    val userId: Int,
    val entryId: Int,
    val points: Int
) {
    companion object {
        val fromRow: (Row) -> VoteRow = { row ->
            VoteRow(
                userId = row.int("user_id"),
                entryId = row.int("entry_id"),
                points = row.int("points"),
            )
        }
    }
}
