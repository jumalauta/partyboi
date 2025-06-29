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
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.Forbidden
import party.jml.partyboi.data.FormError
import party.jml.partyboi.data.isTrue
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asInt
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.validation.MaxLength
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable

class EntryRepository(private val app: AppServices) : Logging() {
    private val db = app.db

    suspend fun getAllEntries(): AppResult<List<Entry>> = db.use {
        it.many(queryOf("select * from entry").map(Entry.fromRow))
    }

    suspend fun getAllEntriesByCompo(): AppResult<Map<Int, List<Entry>>> =
        getAllEntries().map { it.groupBy { it.compoId } }

    suspend fun getEntriesForCompo(compoId: Int): AppResult<List<Entry>> = db.use {
        it.many(queryOf("select * from entry where compo_id = ? order by run_order, id", compoId).map(Entry.fromRow))
    }

    suspend fun get(entryId: Int): AppResult<Entry> = db.use {
        it.one(queryOf("SELECT * FROM entry WHERE id = ?", entryId).map(Entry.fromRow))
    }

    suspend fun get(entryId: Int, userId: Int): AppResult<Entry> = db.use {
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

    suspend fun getUserEntries(userId: Int): AppResult<List<EntryWithLatestFile>> = db.use {
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
                    )
                    newEntry.file.write(fileDesc.storageFilename).bind()
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

    suspend fun update(entry: EntryUpdate, userId: Int): AppResult<Entry> = either {
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

    suspend fun delete(entryId: Int, userId: Int): AppResult<Unit> = either {
        val entry = get(entryId).bind()
        db.use {
            it.updateOne(queryOf("delete from entry where id = ? and user_id = ?", entryId, userId))
        }.onRight {
            app.signals.emit(Signal.compoContentUpdated(entry.compoId, app.time))
        }
    }

    suspend fun delete(entryId: Int): AppResult<Unit> = either {
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

    suspend fun setQualified(entryId: Int, state: Boolean): AppResult<Unit> =
        db.use {
            it.one(queryOf("update entry set qualified = ? where id = ? returning compo_id", state, entryId).map(asInt))
        }.map { compoId ->
            app.signals.emit(Signal.compoContentUpdated(compoId, app.time))
        }

    suspend fun allowEdit(entryId: Int, state: Boolean): AppResult<Unit> =
        db.use {
            it.updateOne(
                queryOf(
                    "update entry set allow_edit = ? where id = ? returning compo_id",
                    state,
                    entryId
                )
            )
        }

    suspend fun assertCanSubmit(entryId: Int, isAdmin: Boolean): AppResult<Unit> = db.use {
        it.one(
            queryOf(
                "select ? or allow_edit from entry where id = ?",
                isAdmin,
                entryId,
            ).map { it.boolean(1) })
            .flatMap { if (it) Unit.right() else Forbidden().left() }
    }

    suspend fun setRunOrder(entryId: Int, order: Int): AppResult<Unit> =
        db.use {
            it.updateOne(queryOf("update entry set run_order = ? where id = ?", order, entryId))
        }

    suspend fun getVotableEntries(userId: Int): AppResult<List<VotableEntry>> = db.use {
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
                    points
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

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.entries.size} entries")
        data.entries.map {
            tx.exec(
                queryOf(
                    "INSERT INTO entry (id, title, author, screen_comment, org_comment, compo_id, user_id, qualified, run_order, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    it.id,
                    it.title,
                    it.author,
                    it.screenComment.getOrNull(),
                    it.orgComment.getOrNull(),
                    it.compoId,
                    it.userId,
                    it.qualified,
                    it.runOrder,
                    it.timestamp,
                )
            )
        }.bindAll()
    }
}

interface EntryBase {
    val id: Int
    val title: String
    val author: String
    val compoId: Int
}

@Serializable
data class Entry(
    override val id: Int,
    override val title: String,
    override val author: String,
    val screenComment: Option<String>,
    val orgComment: Option<String>,
    override val compoId: Int,
    val userId: Int,
    val qualified: Boolean,
    val runOrder: Int,
    val timestamp: Instant,
    val allowEdit: Boolean,
) : EntryBase {
    companion object {
        val fromRow: (Row) -> Entry = { row ->
            Entry(
                row.int("id"),
                row.string("title"),
                row.string("author"),
                Option.fromNullable(row.stringOrNull("screen_comment")),
                Option.fromNullable(row.stringOrNull("org_comment")),
                row.int("compo_id"),
                row.int("user_id"),
                row.boolean("qualified"),
                row.int("run_order"),
                row.instant("timestamp").toKotlinInstant(),
                row.boolean("allow_edit"),
            )
        }
    }
}

data class EntryWithLatestFile(
    override val id: Int,
    override val title: String,
    override val author: String,
    val screenComment: Option<String>,
    val orgComment: Option<String>,
    override val compoId: Int,
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
                row.int("id"),
                row.string("title"),
                row.string("author"),
                Option.fromNullable(row.stringOrNull("screen_comment")),
                Option.fromNullable(row.stringOrNull("org_comment")),
                row.int("compo_id"),
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
    @MaxLength(64)
    val title: String,
    @Field("Author")
    @NotEmpty
    @MaxLength(64)
    val author: String,
    @Field("File")
    @MaxLength(128)
    val file: FileUpload,
    @Field("Compo")
    val compoId: Int,
    @Field("Show message on the screen", presentation = FieldPresentation.large)
    val screenComment: String,
    @Field("Information for organizers", presentation = FieldPresentation.large)
    val orgComment: String,
    val userId: Int,
) : Validateable<NewEntry> {
    companion object {
        val Empty = NewEntry("", "", FileUpload.Empty, 0, "", "", 0)
    }
}

data class EntryUpdate(
    @Field(presentation = FieldPresentation.hidden)
    val id: Int,

    @Field("Title")
    @NotEmpty
    @MaxLength(64)
    val title: String,

    @Field("Author")
    @NotEmpty
    @MaxLength(64)
    val author: String,

    @Field("Upload new version of file")
    val file: FileUpload,

    @Field("Compo")
    val compoId: Int,

    @Field(presentation = FieldPresentation.hidden)
    val userId: Int,

    @Field("Show message on the screen", presentation = FieldPresentation.large)
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
    override val compoId: Int,
    val compoName: String,
    val entryId: Int,
    val runOrder: Int,
    override val title: String,
    override val author: String,
    val points: Option<Int>,
) : EntryBase {
    override val id = entryId

    companion object {
        val fromRow: (Row) -> VotableEntry = { row ->
            VotableEntry(
                compoId = row.int("compo_id"),
                compoName = row.string("compo_name"),
                entryId = row.int("entry_id"),
                runOrder = row.int("run_order"),
                title = row.string("title"),
                author = row.string("author"),
                points = Option.fromNullable(row.intOrNull("points")),
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
        )
    }
}
