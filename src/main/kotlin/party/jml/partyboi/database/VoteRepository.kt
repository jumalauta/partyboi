package party.jml.partyboi.database

import arrow.core.Either
import kotliquery.*
import party.jml.partyboi.errors.AppError

class VoteRepository(private val db: DatabasePool) {
    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS vote (
                user_id integer REFERENCES appuser(id),
                entry_id integer REFERENCES entry(id),
                points integer NOT NULL,
                CONSTRAINT vote_pkey PRIMARY KEY (user_id, entry_id)
            )
        """)
    }

    fun castVote(userId: Int, entryId: Int, points: Int): Either<AppError, Unit> =
        db.use().execAlways(queryOf("""
                INSERT INTO vote (user_id, entry_id, points)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, entry_id) DO UPDATE SET
                    points = EXCLUDED.points
            """.trimIndent(),
            userId,
            entryId,
            points,
        ))

    fun getUserVotes(userId: Int): Either<AppError, List<UserVote>> =
        db.use().many(queryOf("""
                SELECT
                    entry_id,
                    points
                FROM vote
                WHERE vote.user_id = ?
            """.trimIndent(),
            userId,
        ).map(UserVote.fromRow))

    fun getAllResults(): Either<AppError, List<CompoResult>> =
        db.use().many(queryOf("""
            SELECT
                compo_id,
                compo.name as compo_name,
                sum(points) AS points,
                entry_id,
                title,
                author
            FROM vote
            JOIN entry ON entry.id = vote.entry_id
            JOIN compo ON compo.id = entry.compo_id
            GROUP BY compo_id, compo.name, entry_id, title, author
            ORDER BY compo_id, points DESC
        """.trimIndent()).map(CompoResult.fromRow))
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