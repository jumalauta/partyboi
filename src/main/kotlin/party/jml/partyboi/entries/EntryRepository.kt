@file:UseSerializers(
    OptionSerializer::class,
)

package party.jml.partyboi.entries

import arrow.core.*
import arrow.core.raise.either
import arrow.core.serialization.OptionSerializer
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.*
import party.jml.partyboi.db.*
import party.jml.partyboi.entries.NewEntry.Companion.MAX_AUTHOR_LENGTH
import party.jml.partyboi.entries.NewEntry.Companion.MAX_SCREEN_COMMENT_LENGTH
import party.jml.partyboi.entries.NewEntry.Companion.MAX_TITLE_LENGTH
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.validation.MaxLength
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import java.util.*

class EntryRepository(app: AppServices) : Service(app) {
    private val db = app.db

    suspend fun getAllEntries(): AppResult<List<Entry>> = db.use {
        it.many(queryOf("select * from entry").map(Entry.fromRow))
    }

    suspend fun getAllEntriesByCompo(): AppResult<Map<UUID, List<Entry>>> =
        getAllEntries().map { it.groupBy { it.compoId } }

    suspend fun getEntriesForCompo(compoId: UUID): AppResult<List<Entry>> = db.use {
        it.many(queryOf("select * from entry where compo_id = ? order by run_order, id", compoId).map(Entry.fromRow))
    }

    suspend fun get(entryId: UUID): AppResult<Entry> = db.use {
        it.one(queryOf("SELECT * FROM entry WHERE id = ?", entryId).map(Entry.fromRow))
    }

    suspend fun get(entryId: UUID, userId: UUID): AppResult<Entry> = db.use {
        it.one(
            queryOf(
                """
            SELECT *
            FROM entry
            WHERE id = ?
            AND (
            	user_id = ? OR
            	(SELECT is_admin FROM appuser WHERE id = ?)
            )
        """.trimIndent(),
                entryId,
                userId,
                userId,
            ).map(Entry.fromRow)
        )
    }

    suspend fun getUserEntries(userId: UUID): AppResult<List<EntryWithLatestFile>> = db.use {
        it.many(
            query = queryOf(
                """
            SELECT *
            FROM entry
            LEFT JOIN LATERAL(
            	SELECT
            		version,
            		orig_filename,
            		size,
                    uploaded_at
            	FROM file
            	WHERE entry_id = entry.id
            	ORDER BY version
            	DESC LIMIT 1
            ) AS file_info ON 1=1
            WHERE user_id = ?
        """.trimIndent(), userId
            ).map(EntryWithLatestFile.fromRow)
        )
    }

    suspend fun add(newEntry: NewEntry): AppResult<Entry> =
        db.transaction {
            either {
                val compo = app.compos.getById(newEntry.compoId, it).bind()
                if (compo.requireFile.isTrue() && !newEntry.file.isDefined) {
                    FormError("${compo.name} compo requires a file").left().bind<Unit>()
                }

                val entry = it.one(
                    queryOf(
                        "insert into entry(title, author, compo_id, user_id, screen_comment, org_comment) values(?, ?, ?, ?, ?, ?) returning *",
                        newEntry.title,
                        newEntry.author,
                        newEntry.compoId,
                        newEntry.userId,
                        newEntry.screenComment.nonEmptyString(),
                        newEntry.orgComment.nonEmptyString(),
                    ).map(Entry.fromRow)
                ).bind()

                if (newEntry.file.isDefined) {
                    val storageFilename = app.files.makeStorageFilename(entry, newEntry.file.name, it).bind()

                    val fileDesc = NewFileDesc(
                        entryId = entry.id,
                        originalFilename = newEntry.file.name,
                        storageFilename = storageFilename,
                        processed = false,
                        info = null
                    )
                    newEntry.file.writeEntry(fileDesc.storageFilename).bind()
                    val storedFile = app.files.add(fileDesc, it).bind()

                    app.screenshots.scanForScreenshotSource(storedFile).map { source ->
                        app.screenshots.store(entry.id, source)
                    }
                }

                entry
            }
        }.onRight {
            app.signals.emit(Signal.compoContentUpdated(newEntry.compoId, app.time))
        }

    suspend fun update(entry: EntryUpdate, userId: UUID): AppResult<Entry> = either {
        val previousVersion = get(entry.id).bind()
        db.use {
            it.one(
                queryOf(
                    """
            update entry set
                title = ?,
                author = ?,
                compo_id = ?,
                screen_comment = ?,
                org_comment = ?
            where id = ? and (
            	user_id = ? OR
            	(SELECT is_admin FROM appuser WHERE id = ?)
            )
            returning *
            """.trimIndent(),
                    entry.title,
                    entry.author,
                    entry.compoId,
                    entry.screenComment.nonEmptyString(),
                    entry.orgComment.nonEmptyString(),
                    entry.id,
                    userId, userId
                ).map(Entry.fromRow)
            )
        }.onRight {
            app.signals.emit(Signal.compoContentUpdated(entry.compoId, app.time))
            if (previousVersion.compoId != entry.compoId) {
                app.signals.emit(Signal.compoContentUpdated(previousVersion.compoId, app.time))
            }
        }.bind()
    }

    suspend fun delete(entryId: UUID, userId: UUID): AppResult<Unit> = either {
        val entry = get(entryId).bind()
        db.use {
            it.updateOne(queryOf("delete from entry where id = ? and user_id = ?", entryId, userId))
        }.onRight {
            app.signals.emit(Signal.compoContentUpdated(entry.compoId, app.time))
        }
    }

    suspend fun delete(entryId: UUID): AppResult<Unit> = either {
        val entry = get(entryId).bind()
        db.use {
            it.updateOne(queryOf("delete from entry where id = ?", entryId))
        }.onRight {
            app.signals.emit(Signal.compoContentUpdated(entry.compoId, app.time))
        }
    }

    suspend fun deleteAll(): AppResult<Unit> = db.use {
        it.exec(queryOf("delete from entry"))
    }

    suspend fun setQualified(entryId: UUID, state: Boolean): AppResult<Unit> =
        db.use {
            it.one(
                queryOf(
                    "update entry set qualified = ? where id = ? returning compo_id",
                    state,
                    entryId
                ).map({ it.uuid("compo_id") })
            )
        }.map { compoId ->
            app.signals.emit(Signal.compoContentUpdated(compoId, app.time))
        }

    suspend fun allowEdit(entryId: UUID, state: Boolean): AppResult<Unit> =
        db.use {
            it.updateOne(
                queryOf(
                    "update entry set allow_edit = ? where id = ? returning compo_id",
                    state,
                    entryId
                )
            )
        }

    suspend fun assertCanSubmit(entryId: UUID, isAdmin: Boolean): AppResult<Unit> = db.use {
        it.one(
            queryOf(
                "select ? or allow_edit from entry where id = ?",
                isAdmin,
                entryId,
            ).map { it.boolean(1) })
            .flatMap { if (it) Unit.right() else Forbidden().left() }
    }

    suspend fun setRunOrder(entryId: UUID, order: Int): AppResult<Unit> =
        db.use {
            it.updateOne(queryOf("update entry set run_order = ? where id = ?", order, entryId))
        }

    suspend fun getVotableEntries(userId: UUID): AppResult<List<VotableEntry>> = db.use {
        it.many(
            queryOf(
                """
                SELECT
                    compo_id,
                    compo.name AS compo_name,
                    entry.id AS entry_id,
                    run_order,
                    title,
                    author,
                    points,
                    screen_comment
                FROM entry
                JOIN compo ON compo.id = entry.compo_id
                LEFT JOIN vote ON vote.entry_id = entry.id AND vote.user_id = ?
                WHERE qualified AND allow_vote
                ORDER BY compo_id, run_order
            """.trimIndent(),
                userId
            )
                .map(VotableEntry.fromRow)
        )
    }
}

interface EntryBase {
    val id: UUID
    val title: String
    val author: String
    val compoId: UUID
}

@Serializable
data class Entry(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID,
    override val title: String,
    override val author: String,
    val screenComment: Option<String>,
    val orgComment: Option<String>,
    @Serializable(with = UUIDSerializer::class)
    override val compoId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val qualified: Boolean,
    val runOrder: Int,
    val timestamp: Instant,
    val allowEdit: Boolean,
) : EntryBase {
    companion object {
        val fromRow: (Row) -> Entry = { row ->
            Entry(
                row.uuid("id"),
                row.string("title"),
                row.string("author"),
                Option.fromNullable(row.stringOrNull("screen_comment")),
                Option.fromNullable(row.stringOrNull("org_comment")),
                row.uuid("compo_id"),
                row.uuid("user_id"),
                row.boolean("qualified"),
                row.int("run_order"),
                row.instant("timestamp").toKotlinInstant(),
                row.boolean("allow_edit"),
            )
        }
    }
}

data class EntryWithLatestFile(
    override val id: UUID,
    override val title: String,
    override val author: String,
    val screenComment: Option<String>,
    val orgComment: Option<String>,
    override val compoId: UUID,
    val userId: Int,
    val qualified: Boolean,
    val runOrder: Int,
    val timestamp: Instant,
    val originalFilename: Option<String>,
    val fileVersion: Option<Int>,
    val uploadedAt: Option<Instant>,
    val fileSize: Option<Long>,
) : EntryBase {
    companion object {
        val fromRow: (Row) -> EntryWithLatestFile = { row ->
            EntryWithLatestFile(
                row.uuid("id"),
                row.string("title"),
                row.string("author"),
                Option.fromNullable(row.stringOrNull("screen_comment")),
                Option.fromNullable(row.stringOrNull("org_comment")),
                row.uuid("compo_id"),
                row.int("user_id"),
                row.boolean("qualified"),
                row.int("run_order"),
                row.instant("timestamp").toKotlinInstant(),
                row.stringOrNull("orig_filename").toOption(),
                row.intOrNull("version").toOption(),
                row.instantOrNull("uploaded_at")?.toKotlinInstant().toOption(),
                row.longOrNull("size").toOption(),
            )
        }
    }
}

data class NewEntry(
    @Field("Title")
    @NotEmpty
    @MaxLength(MAX_TITLE_LENGTH)
    val title: String,
    @Field("Author")
    @NotEmpty
    @MaxLength(MAX_AUTHOR_LENGTH)
    val author: String,
    @Field("File")
    @MaxLength(128)
    val file: FileUpload,
    @Field("Compo")
    val compoId: UUID,
    @MaxLength(MAX_SCREEN_COMMENT_LENGTH)
    @Field("Public message (shown on screen, voting and results file)", presentation = FieldPresentation.large)
    val screenComment: String,
    @Field("Information for organizers", presentation = FieldPresentation.large)
    val orgComment: String,
    val userId: UUID,
) : Validateable<NewEntry> {
    companion object {
        val Empty = NewEntry(
            title = "",
            author = "",
            file = FileUpload.Empty,
            compoId = UUID.randomUUID(),
            screenComment = "",
            orgComment = "",
            userId = UUID.randomUUID()
        )

        const val MAX_TITLE_LENGTH = 128
        const val MAX_AUTHOR_LENGTH = 128
        const val MAX_SCREEN_COMMENT_LENGTH = 512
    }
}

data class EntryUpdate(
    @Field(presentation = FieldPresentation.hidden)
    val id: UUID,

    @Field("Title")
    @NotEmpty
    @MaxLength(MAX_TITLE_LENGTH)
    val title: String,

    @Field("Author")
    @NotEmpty
    @MaxLength(MAX_AUTHOR_LENGTH)
    val author: String,

    @Field("Upload new version of file")
    val file: FileUpload,

    @Field("Compo")
    val compoId: UUID,

    @Field(presentation = FieldPresentation.hidden)
    val userId: UUID,

    @Field("Public message (shown on screen, voting and results file)", presentation = FieldPresentation.large)
    @MaxLength(MAX_SCREEN_COMMENT_LENGTH)
    val screenComment: String,

    @Field("Information for organizers", presentation = FieldPresentation.large)
    val orgComment: String,
) : Validateable<EntryUpdate> {
    companion object {
        fun fromEntry(e: Entry) = EntryUpdate(
            id = e.id,
            title = e.title,
            author = e.author,
            file = FileUpload.Empty,
            compoId = e.compoId,
            userId = e.userId,
            screenComment = e.screenComment.getOrElse { "" },
            orgComment = e.orgComment.getOrElse { "" },
        )
    }
}

data class VotableEntry(
    override val compoId: UUID,
    val compoName: String,
    val entryId: UUID,
    val runOrder: Int,
    override val title: String,
    override val author: String,
    val points: Option<Int>,
    val info: Option<String>,
) : EntryBase {
    override val id = entryId

    companion object {
        val fromRow: (Row) -> VotableEntry = { row ->
            VotableEntry(
                compoId = row.uuid("compo_id"),
                compoName = row.string("compo_name"),
                entryId = row.uuid("entry_id"),
                runOrder = row.int("run_order"),
                title = row.string("title"),
                author = row.string("author"),
                points = Option.fromNullable(row.intOrNull("points")),
                info = row.stringOrNull("screen_comment")?.nonEmptyString().toOption(),
            )
        }

        fun apply(entry: Entry, compoName: String, points: Int?) = VotableEntry(
            compoId = entry.compoId,
            compoName = compoName,
            entryId = entry.id,
            runOrder = entry.runOrder,
            title = entry.title,
            author = entry.author,
            points = Option.fromNullable(points),
            info = entry.screenComment,
        )
    }
}
