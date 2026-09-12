package party.jml.partyboi.sync

import arrow.core.raise.either
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.db.*
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import java.util.*

/**
 * Reconciliation of duplicate rows created when two instances both accept
 * submissions during a sync split (e.g. a fallback instance on party day).
 * Sync merges rows by UUID only, so the same prod or person on two instances
 * becomes two unrelated rows; this service finds likely duplicates and lets
 * an admin merge them.
 */
class ReconciliationService(app: AppServices) : Service(app) {
    private val db = app.db

    /**
     * Entry pairs in the same compo that share a file checksum (near-certain
     * duplicates) or a normalized title+author (probable duplicates).
     * Pairs the admin has dismissed are excluded.
     */
    suspend fun duplicateEntryCandidates(): AppResult<List<DuplicateEntryCandidate>> = db.use {
        many(
            queryOf(
                """
                SELECT
                    c.name AS compo_name,
                    e1.id AS id_a, e1.title AS title_a, e1.author AS author_a, e1.origin AS origin_a,
                    e1.timestamp AS timestamp_a, u1.name AS user_a,
                    (SELECT count(*) FROM vote WHERE entry_id = e1.id) AS votes_a,
                    (SELECT count(*) FROM entry_file WHERE entry_id = e1.id) AS files_a,
                    e2.id AS id_b, e2.title AS title_b, e2.author AS author_b, e2.origin AS origin_b,
                    e2.timestamp AS timestamp_b, u2.name AS user_b,
                    (SELECT count(*) FROM vote WHERE entry_id = e2.id) AS votes_b,
                    (SELECT count(*) FROM entry_file WHERE entry_id = e2.id) AS files_b,
                    $CHECKSUM_MATCH AS checksum_match
                FROM entry e1
                JOIN entry e2 ON e2.compo_id = e1.compo_id AND e1.id < e2.id
                JOIN compo c ON c.id = e1.compo_id
                JOIN appuser u1 ON u1.id = e1.user_id
                JOIN appuser u2 ON u2.id = e2.user_id
                WHERE (
                    (lower(trim(e1.title)) = lower(trim(e2.title)) AND lower(trim(e1.author)) = lower(trim(e2.author)))
                    OR $CHECKSUM_MATCH
                )
                AND NOT EXISTS (
                    SELECT 1 FROM dismissed_duplicate d
                    WHERE d.kind = 'entry' AND d.id_a = e1.id AND d.id_b = e2.id
                )
                ORDER BY c.name, e1.title
                """.trimIndent()
            ).map { row ->
                DuplicateEntryCandidate(
                    compoName = row.string("compo_name"),
                    a = EntryCandidateSide.fromRow(row, "a"),
                    b = EntryCandidateSide.fromRow(row, "b"),
                    checksumMatch = row.boolean("checksum_match"),
                )
            }
        )
    }

    /**
     * User pairs that look like the same person registered on both instances:
     * equal names modulo the "-XXX-YYY" suffix the sync collision resolver
     * appends, or an equal email address.
     */
    suspend fun duplicateUserCandidates(): AppResult<List<DuplicateUserCandidate>> = db.use {
        many(
            queryOf(
                """
                SELECT
                    u1.id AS id_a, u1.name AS name_a, u1.email AS email_a, u1.origin AS origin_a,
                    (SELECT count(*) FROM entry WHERE user_id = u1.id) AS entries_a,
                    (SELECT count(*) FROM vote WHERE user_id = u1.id) AS votes_a,
                    u2.id AS id_b, u2.name AS name_b, u2.email AS email_b, u2.origin AS origin_b,
                    (SELECT count(*) FROM entry WHERE user_id = u2.id) AS entries_b,
                    (SELECT count(*) FROM vote WHERE user_id = u2.id) AS votes_b
                FROM appuser u1
                JOIN appuser u2 ON u1.id < u2.id
                WHERE (
                    regexp_replace(u1.name, '$RENAME_SUFFIX_PATTERN', '') = regexp_replace(u2.name, '$RENAME_SUFFIX_PATTERN', '')
                    OR (u1.email IS NOT NULL AND lower(u1.email) = lower(u2.email))
                )
                AND NOT EXISTS (
                    SELECT 1 FROM dismissed_duplicate d
                    WHERE d.kind = 'user' AND d.id_a = u1.id AND d.id_b = u2.id
                )
                ORDER BY name_a
                """.trimIndent()
            ).map { row ->
                DuplicateUserCandidate(
                    a = UserCandidateSide.fromRow(row, "a"),
                    b = UserCandidateSide.fromRow(row, "b"),
                )
            }
        )
    }

    /**
     * Merge the loser entry into the survivor: files are repointed, the
     * loser's preview is kept only when the survivor has none, and ballots
     * follow the entry — when the same voter voted on both copies, the higher
     * points win. The loser row is then deleted (cascade removes its
     * remaining votes and preview).
     */
    suspend fun mergeEntries(survivorId: UUID, loserId: UUID): AppResult<Unit> = either {
        val compoId = db.transaction {
            either {
                if (survivorId == loserId) {
                    raise(ValidationError("entry", "Cannot merge an entry into itself", survivorId.toString()))
                }
                val survivorCompo = one(
                    queryOf("SELECT compo_id FROM entry WHERE id = ?", survivorId).map { it.uuid("compo_id") }
                ).bind()
                val loserCompo = one(
                    queryOf("SELECT compo_id FROM entry WHERE id = ?", loserId).map { it.uuid("compo_id") }
                ).bind()
                if (survivorCompo != loserCompo) {
                    raise(ValidationError("entry", "Entries must be in the same compo", loserId.toString()))
                }

                exec(queryOf("UPDATE entry_file SET entry_id = ? WHERE entry_id = ?", survivorId, loserId)).bind()
                exec(
                    queryOf(
                        """
                        UPDATE preview SET entry_id = ? WHERE entry_id = ?
                        AND NOT EXISTS (SELECT 1 FROM preview WHERE entry_id = ?)
                        """.trimIndent(),
                        survivorId, loserId, survivorId
                    )
                ).bind()
                exec(
                    queryOf(
                        """
                        INSERT INTO vote (user_id, entry_id, points)
                        SELECT user_id, ?, points FROM vote WHERE entry_id = ?
                        ON CONFLICT (user_id, entry_id) DO UPDATE SET
                            points = GREATEST(vote.points, EXCLUDED.points)
                        """.trimIndent(),
                        survivorId, loserId
                    )
                ).bind()
                updateOne(queryOf("DELETE FROM entry WHERE id = ?", loserId)).bind()
                exec(queryOf("DELETE FROM dismissed_duplicate WHERE id_a = ? OR id_b = ?", loserId, loserId)).bind()

                survivorCompo
            }
        }.bind()
        app.signals.emit(Signal.compoContentUpdated(compoId, app.time))
    }

    /**
     * Merge the loser user into the survivor: entries, ballots, messages and
     * (when the survivor has none) vote keys are repointed; on ballot
     * collisions the higher points win; an admin bit on either account is
     * kept. The loser row is then deleted (cascade removes its remaining
     * votes and vote keys).
     */
    suspend fun mergeUsers(survivorId: UUID, loserId: UUID): AppResult<Unit> =
        db.transaction {
            either {
                if (survivorId == loserId) {
                    raise(ValidationError("user", "Cannot merge a user into itself", survivorId.toString()))
                }
                one(queryOf("SELECT id FROM appuser WHERE id = ?", survivorId).map { it.uuid("id") }).bind()
                one(queryOf("SELECT id FROM appuser WHERE id = ?", loserId).map { it.uuid("id") }).bind()

                exec(queryOf("UPDATE entry SET user_id = ? WHERE user_id = ?", survivorId, loserId)).bind()
                exec(
                    queryOf(
                        """
                        INSERT INTO vote (user_id, entry_id, points)
                        SELECT ?, entry_id, points FROM vote WHERE user_id = ?
                        ON CONFLICT (user_id, entry_id) DO UPDATE SET
                            points = GREATEST(vote.points, EXCLUDED.points)
                        """.trimIndent(),
                        survivorId, loserId
                    )
                ).bind()
                exec(
                    queryOf(
                        """
                        UPDATE votekey SET user_id = ? WHERE user_id = ?
                        AND NOT EXISTS (SELECT 1 FROM votekey WHERE user_id = ?)
                        """.trimIndent(),
                        survivorId, loserId, survivorId
                    )
                ).bind()
                exec(queryOf("UPDATE message SET user_id = ? WHERE user_id = ?", survivorId, loserId)).bind()
                exec(
                    queryOf(
                        "UPDATE appuser SET is_admin = TRUE WHERE id = ? AND (SELECT is_admin FROM appuser WHERE id = ?)",
                        survivorId, loserId
                    )
                ).bind()
                // The session table has no FK to appuser, so deleting the loser would otherwise leave
                // its sessions valid under a now-nonexistent identity — drop them explicitly.
                exec(queryOf("DELETE FROM session WHERE user_id = ?", loserId)).bind()
                updateOne(queryOf("DELETE FROM appuser WHERE id = ?", loserId)).bind()
                exec(queryOf("DELETE FROM dismissed_duplicate WHERE id_a = ? OR id_b = ?", loserId, loserId)).bind()
            }
        }

    /** Mark a candidate pair as "not a duplicate" so it stops appearing. */
    suspend fun dismiss(kind: DuplicateKind, idA: UUID, idB: UUID): AppResult<Unit> = db.use {
        exec(
            queryOf(
                """
                INSERT INTO dismissed_duplicate (kind, id_a, id_b)
                VALUES (?, least(?::uuid, ?::uuid), greatest(?::uuid, ?::uuid))
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                kind.key, idA, idB, idA, idB
            )
        )
    }

    companion object {
        // The appuser sync collision resolver renames "name" to "name-ABC-DEF"
        // (randomShortId: two groups of three uppercase letters).
        private const val RENAME_SUFFIX_PATTERN = """-[A-Z]{3}-[A-Z]{3}${'$'}"""

        private const val CHECKSUM_MATCH = """
            EXISTS (
                SELECT 1
                FROM entry_file ef1
                JOIN file f1 ON f1.id = ef1.file_id
                JOIN entry_file ef2 ON ef2.entry_id = e2.id
                JOIN file f2 ON f2.id = ef2.file_id
                WHERE ef1.entry_id = e1.id
                  AND f1.checksum IS NOT NULL
                  AND f1.checksum = f2.checksum
            )
        """
    }
}

enum class DuplicateKind(val key: String) {
    Entry("entry"),
    User("user"),
}

data class DuplicateEntryCandidate(
    val compoName: String,
    val a: EntryCandidateSide,
    val b: EntryCandidateSide,
    val checksumMatch: Boolean,
)

data class EntryCandidateSide(
    val id: UUID,
    val title: String,
    val author: String,
    val userName: String,
    val origin: String?,
    val timestamp: Instant,
    val voteCount: Long,
    val fileCount: Long,
) {
    companion object {
        fun fromRow(row: Row, side: String) = EntryCandidateSide(
            id = row.uuid("id_$side"),
            title = row.string("title_$side"),
            author = row.string("author_$side"),
            userName = row.string("user_$side"),
            origin = row.stringOrNull("origin_$side"),
            timestamp = row.instant("timestamp_$side").toKotlinInstant(),
            voteCount = row.long("votes_$side"),
            fileCount = row.long("files_$side"),
        )
    }
}

data class DuplicateUserCandidate(
    val a: UserCandidateSide,
    val b: UserCandidateSide,
) {
    val emailMatch: Boolean =
        a.email != null && b.email != null && a.email.lowercase() == b.email.lowercase()
}

data class UserCandidateSide(
    val id: UUID,
    val name: String,
    val email: String?,
    val origin: String?,
    val entryCount: Long,
    val voteCount: Long,
) {
    companion object {
        fun fromRow(row: Row, side: String) = UserCandidateSide(
            id = row.uuid("id_$side"),
            name = row.string("name_$side"),
            email = row.stringOrNull("email_$side"),
            origin = row.stringOrNull("origin_$side"),
            entryCount = row.long("entries_$side"),
            voteCount = row.long("votes_$side"),
        )
    }
}
