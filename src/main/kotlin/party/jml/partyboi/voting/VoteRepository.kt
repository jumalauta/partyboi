package party.jml.partyboi.voting

import arrow.core.Either
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.data.AppError
import party.jml.partyboi.db.DatabasePool
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many

class VoteRepository(private val db: DatabasePool) {
    fun castVote(userId: Int, entryId: Int, points: Int): Either<AppError, Unit> = db.use {
        it.exec(
            queryOf(
                """
                INSERT INTO vote (user_id, entry_id, points)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, entry_id) DO UPDATE SET
                    points = EXCLUDED.points
            """.trimIndent(),
                userId,
                entryId,
                points,
            )
        )
    }

    fun getUserVotes(userId: Int): Either<AppError, List<UserVote>> = db.use {
        it.many(
            queryOf(
                """
                SELECT
                    entry_id,
                    points
                FROM vote
                WHERE vote.user_id = ?
            """.trimIndent(),
                userId,
            ).map(UserVote.fromRow)
        )
    }

    fun getResults(onlyPublic: Boolean): Either<AppError, List<CompoResult>> = db.use {
        it.many(
            queryOf(
                """
            SELECT
                compo_id,
                compo.name as compo_name,
                coalesce(sum(points), 0) AS points,
                entry.id AS entry_id,
                title,
                author
            FROM entry
            LEFT JOIN vote ON entry.id = vote.entry_id
            JOIN compo ON compo.id = entry.compo_id
            WHERE qualified
            ${if (onlyPublic) "AND public_results" else ""}
            GROUP BY compo_id, compo.name, entry.id, title, author
            ORDER BY compo_id, points DESC
        """.trimIndent()
            ).map(CompoResult.fromRow)
        )
    }
}

data class CompoResult(
    val compoId: Int,
    val compoName: String,
    val points: Int,
    val entryId: Int,
    val title: String,
    val author: String
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

data class UserVote(
    val entryId: Int,
    val points: Int
) {
    companion object {
        val fromRow: (Row) -> UserVote = { row ->
            UserVote(
                entryId = row.int("entry_id"),
                points = row.int("points"),
            )
        }
    }
}