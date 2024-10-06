package party.jml.partyboi.database

import arrow.core.Either
import arrow.core.Option
import arrow.core.flatten
import arrow.core.raise.either
import arrow.core.toOption
import kotlinx.coroutines.runBlocking
import kotlinx.html.InputType
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.NotFound
import party.jml.partyboi.errors.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FileUpload
import java.time.LocalDateTime

class EntryRepository(private val app: AppServices) {
    private val db = app.db

    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS entry (
                id SERIAL PRIMARY KEY,
                title text NOT NULL,
                author text NOT NULL,
                screen_comment text,
                org_comment text,
                compo_id integer REFERENCES compo(id),
                user_id integer REFERENCES appuser(id),
                qualified boolean NOT NULL DEFAULT true,
                run_order integer NOT NULL DEFAULT 0,
                timestamp timestamp with time zone DEFAULT now()
            );
        """)
    }

    fun getAllEntries(): Either<AppError, List<Entry>> =
        db.use().many(queryOf("select * from entry").map(Entry.fromRow))

    fun getAllEntriesByCompo(): Either<AppError, Map<Int, List<Entry>>> =
        getAllEntries().map { it.groupBy { it.compoId } }

    fun getEntriesForCompo(compoId: Int): Either<AppError, List<Entry>> =
        db.use().many(queryOf("select * from entry where compo_id = ? order by run_order, id", compoId).map(Entry.fromRow))

    fun get(entryId: Int, userId: Int): Either<AppError, Entry> =
        db.use().one(queryOf("select * from entry where id = ? and user_id = ?", entryId, userId).map(Entry.fromRow))

    fun getUserEntries(userId: Int): Either<AppError, List<EntryWithLatestFile>> =
        db.use().many(query = queryOf("""
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
            ) ON 1=1
            WHERE user_id = ?
        """.trimIndent(), userId).map(EntryWithLatestFile.fromRow))

    fun add(newEntry: NewEntry): Either<AppError, Unit> =
        db.transaction { either {
            val entry = it.one(queryOf(
            "insert into entry(title, author, filename, compo_id, user_id) values(?, ?, ?, ?, ?) returning *",
                newEntry.title,
                newEntry.author,
                newEntry.file.name,
                newEntry.compoId,
                newEntry.userId,
            ).map(Entry.fromRow)).bind()

            val storageFilename = app.files.makeStorageFilename(entry, newEntry.file.name, it).bind()

            val fileDesc = NewFileDesc(
                entryId = entry.id,
                originalFilename = newEntry.file.name,
                storageFilename = storageFilename,
            )
            newEntry.file.write(fileDesc.storageFilename).bind()
            app.files.add(fileDesc, it).bind()
        } }

    fun update(entry: EntryUpdate, userId: Int): Either<AppError, Unit> =
        db.use().updateOne(queryOf("""
            update entry set
                title = ?,
                author = ?,
                filename = coalesce(?, filename),
                compo_id = ?
            where id = ? and user_id = ?
            """.trimIndent(),
            entry.title,
            entry.author,
            entry.file.name.nonEmptyString(),
            entry.compoId,
            entry.id,
            userId
        ))

    fun delete(entryId: Int, userId: Int): Either<AppError, Unit> =
        db.use().updateOne(queryOf("delete from entry where id = ? and user_id = ?", entryId, userId))

    fun delete(id: Int): Either<AppError, Unit> =
        db.use().updateOne(queryOf("delete from entry where id = ?", id))

    fun setQualified(entryId: Int, state: Boolean): Either<AppError, Unit> =
        db.use().updateOne(queryOf("update entry set qualified = ? where id = ?", state, entryId))

    fun setRunOrder(entryId: Int, order: Int): Either<AppError, Unit> =
        db.use().updateOne(queryOf("update entry set run_order = ? where id = ?", order, entryId))

    fun getVotableEntries(userId: Int): Either<AppError, List<VoteableEntry>> =
        db.use().many(queryOf("""
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
            userId)
            .map(VoteableEntry.fromRow)
        )
}

data class Entry(
    val id: Int,
    val title: String,
    val author: String,
    val screenComment: Option<String>,
    val orgComment: Option<String>,
    val compoId: Int,
    val userId: Int,
    val qualified: Boolean,
    val runOrder: Int,
    val timestamp: LocalDateTime,
) {
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
                row.localDateTime("timestamp")
            )
        }
    }
}

data class EntryWithLatestFile(
    val id: Int,
    val title: String,
    val author: String,
    val screenComment: Option<String>,
    val orgComment: Option<String>,
    val compoId: Int,
    val userId: Int,
    val qualified: Boolean,
    val runOrder: Int,
    val timestamp: LocalDateTime,
    val originalFilename: Option<String>,
    val fileVersion: Option<Int>,
    val uploadedAt: Option<LocalDateTime>,
    val fileSize: Option<Long>,
) {
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
                row.localDateTime("timestamp"),
                row.stringOrNull("orig_filename").toOption(),
                row.intOrNull("version").toOption(),
                row.localDateTimeOrNull("uploaded_at").toOption(),
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
    val userId: Int,
) : Validateable<NewEntry> {
    override fun validationErrors(): List<Option<ValidationError.Message>> {
        return listOf(
            expectNotEmpty("title", title),
            expectMaxLength("title", title, 64),
            expectNotEmpty("author", author),
            expectMaxLength("author", author, 64),
            expectNotEmpty("file", file.name),
            expectMaxLength("file", file.name, 128),
        )
    }

    companion object {
        val Empty = NewEntry("", "", FileUpload.Empty, 0, 0)
    }
}

data class EntryUpdate(
    @property:Field(type = InputType.hidden)
    val id: Int,
    @property:Field(2, "Title")
    val title: String,
    @property:Field(3, "Author")
    val author: String,
    @property:Field(4, "Upload new version of file")
    val file: FileUpload,
    @property:Field(1, "Compo")
    val compoId: Int,
    @property:Field(type = InputType.hidden)
    val userId: Int,
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
            userId = e.userId
        )
    }
}

data class VoteableEntry(
        val compoId: Int,
        val compoName: String,
        val entryId: Int,
        val runOrder: Int,
        val title: String,
        val author: String,
        val points: Option<Int>,
) {
    companion object {
        val fromRow: (Row) -> VoteableEntry = { row ->
            VoteableEntry(
                compoId = row.int("compo_id"),
                compoName = row.string("compo_name"),
                entryId = row.int("entry_id"),
                runOrder = row.int("run_order"),
                title = row.string("title"),
                author = row.string("author"),
                points = Option.fromNullable(row.intOrNull("points")),
            )
        }
    }
}