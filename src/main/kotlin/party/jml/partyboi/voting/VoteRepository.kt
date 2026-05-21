package party.jml.partyboi.voting

import kotlinx.serialization.Serializable
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.UUIDSerializer
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
        exec(
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
        many(
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
        many(queryOf("SELECT * FROM vote").map(VoteRow.fromRow))
    }

    suspend fun getResults(onlyPublic: Boolean): AppResult<List<CompoResult>> = db.use {
        many(
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
        exec(queryOf("DELETE FROM vote"))
    }
}

data class CompoResult(
    val compoId: UUID,
    val compoName: String,
    val points: Int,
    val entryId: UUID,
    val title: String,
    val author: String,
    val info: String?,
    val downloadLink: String?,
    val scoreText: String? = null,
    val isManual: Boolean = false,
    val position: Int = 0,
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
                info = row.stringOrNull("screen_comment")?.nonEmptyString(),
                downloadLink = null,
            )
        }

        fun groupResults(results: List<CompoResult>): Map<CompoGroup, List<GroupedCompoResult>> = results
            .groupBy { CompoGroup(it.compoId, it.compoName) }
            .mapValues { (_, compoResults) ->
                if (compoResults.any { it.isManual }) {
                    compoResults
                        .sortedBy { it.position }
                        .mapIndexed { index, result -> GroupedCompoResult(index + 1, listOf(result)) }
                } else {
                    val grouped = compoResults
                        .sortedBy { -it.points }
                        .groupBy { it.points }
                        .map { it.value }
                    val places = grouped.scan(1) { place, rs -> place + rs.size }
                    places.zip(grouped).map { (place, rs) -> GroupedCompoResult(place, rs) }
                }
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

@Serializable
data class VoteRow(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val entryId: UUID,
    val points: Int
) {
    companion object {
        val fromRow: (Row) -> VoteRow = { row ->
            VoteRow(
                userId = row.uuid("user_id"),
                entryId = row.uuid("entry_id"),
                points = row.int("points"),
            )
        }
    }
}
