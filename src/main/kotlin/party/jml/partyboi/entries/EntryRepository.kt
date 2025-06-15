@file:UseSerializers(
    OptionSerializer::class,
    LocalDateTimeIso8601Serializer::class,
)

package party.jml.partyboi.entries

import arrow.core.*
import arrow.core.raise.either
import arrow.core.serialization.OptionSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.*
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asInt
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal

class EntryRepository(private val app: AppServices) : Logging() {
    private val db = app.db

    fun getAllEntries(): Either<AppError, List<Entry>> = db.use {
        it.many(queryOf("select * from entry").map(Entry.fromRow))
    }

    fun getAllEntriesByCompo(): Either<AppError, Map<Int, List<Entry>>> =
        getAllEntries().map { it.groupBy { it.compoId } }

    fun getEntriesForCompo(compoId: Int): Either<AppError, List<Entry>> = db.use {
        it.many(queryOf("select * from entry where compo_id = ? order by run_order, id", compoId).map(Entry.fromRow))
    }

    fun get(entryId: Int): Either<AppError, Entry> = db.use {
        it.one(queryOf("SELECT * FROM entry WHERE id = ?", entryId).map(Entry.fromRow))
    }

    fun get(entryId: Int, userId: Int): Either<AppError, Entry> = db.use {
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

    fun getUserEntries(userId: Int): Either<AppError, List<EntryWithLatestFile>> = db.use {
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

    fun add(newEntry: NewEntry): Either<AppError, Entry> =
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
                    runBlocking { newEntry.file.write(fileDesc.storageFilename).bind() }
                    val storedFile = app.files.add(fileDesc, it).bind()

                    app.screenshots.scanForScreenshotSource(storedFile).map { source ->
                        app.screenshots.store(entry.id, source)
                    }
                }

                entry
            }
        }.onRight {
            runBlocking { app.signals.emit(Signal.compoContentUpdated(newEntry.compoId, app.time)) }
        }

    fun update(entry: EntryUpdate, userId: Int): Either<AppError, Entry> = either {
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
            runBlocking {
                app.signals.emit(Signal.compoContentUpdated(entry.compoId, app.time))
                if (previousVersion.compoId != entry.compoId) {
                    app.signals.emit(Signal.compoContentUpdated(previousVersion.compoId, app.time))
                }
            }
        }.bind()
    }

    fun delete(entryId: Int, userId: Int): Either<AppError, Unit> = either {
        val entry = get(entryId).bind()
        db.use {
            it.updateOne(queryOf("delete from entry where id = ? and user_id = ?", entryId, userId))
        }.onRight {
            runBlocking { app.signals.emit(Signal.compoContentUpdated(entry.compoId, app.time)) }
        }
    }

    fun delete(entryId: Int): Either<AppError, Unit> = either {
        val entry = get(entryId).bind()
        db.use {
            it.updateOne(queryOf("delete from entry where id = ?", entryId))
        }.onRight {
            runBlocking { app.signals.emit(Signal.compoContentUpdated(entry.compoId, app.time)) }
        }
    }

    fun deleteAll(): Either<AppError, Unit> = db.use {
        it.exec(queryOf("delete from entry"))
    }

    fun setQualified(entryId: Int, state: Boolean): Either<AppError, Unit> =
        db.use {
            it.one(queryOf("update entry set qualified = ? where id = ? returning compo_id", state, entryId).map(asInt))
        }.map { compoId ->
            runBlocking { app.signals.emit(Signal.compoContentUpdated(compoId, app.time)) }
        }

    fun allowEdit(entryId: Int, state: Boolean): Either<AppError, Unit> =
        db.use {
            it.updateOne(
                queryOf(
                    "update entry set allow_edit = ? where id = ? returning compo_id",
                    state,
                    entryId
                )
            )
        }

    fun assertCanSubmit(entryId: Int, isAdmin: Boolean): Either<AppError, Unit> = db.use {
        it.one(
            queryOf(
                "select ? or allow_edit from entry where id = ?",
                isAdmin,
                entryId,
            ).map { it.boolean(1) })
            .flatMap { if (it) Unit.right() else Forbidden().left() }
    }

    fun setRunOrder(entryId: Int, order: Int): Either<AppError, Unit> =
        db.use {
            it.updateOne(queryOf("update entry set run_order = ? where id = ?", order, entryId))
        }

    fun getVotableEntries(userId: Int): Either<AppError, List<VotableEntry>> = db.use {
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
                    it.timestamp.toJavaLocalDateTime(),
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
    val timestamp: LocalDateTime,
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
                row.localDateTime("timestamp").toKotlinLocalDateTime(),
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
    val timestamp: LocalDateTime,
    val originalFilename: Option<String>,
    val fileVersion: Option<Int>,
    val uploadedAt: Option<LocalDateTime>,
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
                row.localDateTime("timestamp").toKotlinLocalDateTime(),
                row.stringOrNull("orig_filename").toOption(),
                row.intOrNull("version").toOption(),
                row.localDateTimeOrNull("uploaded_at")?.toKotlinLocalDateTime().toOption(),
                row.longOrNull("size").toOption(),
            )
        }
    }
}

data class NewEntry(
    @property:Field(2, "Title")
    val title: String,
    @property:Field(3, "Author")
    val author: String,
    @property:Field(4, "File")
    val file: FileUpload,
    @property:Field(1, "Compo")
    val compoId: Int,
    @property:Field(5, "Show message on the screen", presentation = FieldPresentation.large)
    val screenComment: String,
    @property:Field(6, "Information for organizers", presentation = FieldPresentation.large)
    val orgComment: String,
    val userId: Int,
) : Validateable<NewEntry> {
    override fun validationErrors(): List<Option<ValidationError.Message>> {
        return listOf(
            expectNotEmpty("title", title),
            expectMaxLength("title", title, 64),
            expectNotEmpty("author", author),
            expectMaxLength("author", author, 64),
            expectMaxLength("file", file.name, 128),
        )
    }

    companion object {
        val Empty = NewEntry("", "", FileUpload.Empty, 0, "", "", 0)
    }
}

data class EntryUpdate(
    @property:Field(presentation = FieldPresentation.hidden)
    val id: Int,
    @property:Field(2, "Title")
    val title: String,
    @property:Field(3, "Author")
    val author: String,
    @property:Field(4, "Upload new version of file")
    val file: FileUpload,
    @property:Field(1, "Compo")
    val compoId: Int,
    @property:Field(presentation = FieldPresentation.hidden)
    val userId: Int,
    @property:Field(5, "Show message on the screen", presentation = FieldPresentation.large)
    val screenComment: String,
    @property:Field(6, "Information for organizers", presentation = FieldPresentation.large)
    val orgComment: String,
) : Validateable<EntryUpdate> {
    override fun validationErrors(): List<Option<ValidationError.Message>> {
        return listOf(
            expectNotEmpty("title", title),
            expectMaxLength("title", title, 64),
            expectNotEmpty("author", author),
            expectMaxLength("author", author, 64),
        )
    }

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
