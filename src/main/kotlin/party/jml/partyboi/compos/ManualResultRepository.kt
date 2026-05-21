package party.jml.partyboi.compos

import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.db.*
import party.jml.partyboi.form.*
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.validation.MaxLength
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import party.jml.partyboi.voting.CompoResult
import java.util.*

class ManualResultRepository(app: AppServices) : Service(app) {
    private val db = app.db

    suspend fun getByCompoId(compoId: UUID): AppResult<List<ManualResult>> = db.use {
        many(
            queryOf(
                "SELECT * FROM manual_result WHERE compo_id = ? ORDER BY position",
                compoId
            ).map(ManualResult.fromRow)
        )
    }

    suspend fun countByCompo(): AppResult<Map<UUID, Int>> = db.use {
        many(
            queryOf("SELECT compo_id, count(*) AS n FROM manual_result GROUP BY compo_id")
                .map { row -> row.uuid("compo_id") to row.int("n") }
        ).map { it.toMap() }
    }

    suspend fun add(result: NewManualResult): AppResult<ManualResult> = db.use {
        one(
            queryOf(
                """
                INSERT INTO manual_result (compo_id, title, author, score_text, screen_comment, position)
                VALUES (?, ?, ?, ?, ?, (SELECT coalesce(max(position), 0) + 1 FROM manual_result WHERE compo_id = ?))
                RETURNING *
                """,
                result.compoId,
                result.title,
                result.author,
                result.scoreText,
                result.screenComment.ifBlank { null },
                result.compoId,
            ).map(ManualResult.fromRow)
        )
    }

    suspend fun update(result: ManualResult): AppResult<Unit> = db.use {
        updateOne(
            queryOf(
                """
                UPDATE manual_result
                SET title = ?, author = ?, score_text = ?, screen_comment = ?
                WHERE id = ?
                """,
                result.title,
                result.author,
                result.scoreText,
                result.screenComment,
                result.id,
            )
        )
    }

    suspend fun delete(id: UUID): AppResult<Unit> = db.use {
        exec(queryOf("DELETE FROM manual_result WHERE id = ?", id))
    }

    suspend fun setPosition(id: UUID, position: Int): AppResult<Unit> = db.use {
        updateOne(queryOf("UPDATE manual_result SET position = ? WHERE id = ?", position, id))
    }

    suspend fun getResults(onlyPublic: Boolean): AppResult<List<CompoResult>> = db.use {
        many(
            queryOf(
                """
                SELECT
                    compo_id,
                    compo.name AS compo_name,
                    manual_result.id AS entry_id,
                    title,
                    author,
                    screen_comment,
                    score_text,
                    position
                FROM manual_result
                JOIN compo ON compo.id = manual_result.compo_id
                WHERE compo.manual_results
                ${if (onlyPublic) "AND public_results" else ""}
                ORDER BY compo_id, position
                """.trimIndent()
            ).map { row ->
                CompoResult(
                    compoId = row.uuid("compo_id"),
                    compoName = row.string("compo_name"),
                    points = 0,
                    entryId = row.uuid("entry_id"),
                    title = row.string("title"),
                    author = row.string("author"),
                    info = row.stringOrNull("screen_comment")?.nonEmptyString(),
                    downloadLink = null,
                    scoreText = row.stringOrNull("score_text")?.nonEmptyString(),
                    isManual = true,
                    position = row.int("position"),
                )
            }
        )
    }

    suspend fun deleteAll(): AppResult<Unit> = db.use {
        exec(queryOf("DELETE FROM manual_result"))
    }
}

data class ManualResult(
    val id: UUID,
    val compoId: UUID,
    val title: String,
    val author: String,
    val scoreText: String,
    val screenComment: String?,
    val position: Int,
) {
    companion object {
        val fromRow: (Row) -> ManualResult = { row ->
            ManualResult(
                id = row.uuid("id"),
                compoId = row.uuid("compo_id"),
                title = row.string("title"),
                author = row.string("author"),
                scoreText = row.string("score_text"),
                screenComment = row.stringOrNull("screen_comment")?.nonEmptyString(),
                position = row.int("position"),
            )
        }
    }
}

data class NewManualResult(
    @Label("Title (optional)")
    @MaxLength(128)
    val title: String,

    @Label("Author")
    @NotEmpty
    @MaxLength(128)
    val author: String,

    @Label("Score")
    @MaxLength(64)
    val scoreText: String,

    @Label("Screen comment")
    @MaxLength(512)
    val screenComment: String,

    val compoId: UUID,
) : Validateable<NewManualResult> {
    companion object {
        fun empty(compoId: UUID) = NewManualResult("", "", "", "", compoId)
    }
}
