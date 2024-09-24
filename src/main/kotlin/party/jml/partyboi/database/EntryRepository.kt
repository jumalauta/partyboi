package party.jml.partyboi.database

import arrow.core.Either
import arrow.core.Option
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FileUpload
import java.time.LocalDateTime

class EntryRepository(private val db: DatabasePool) {
    fun getAllEntries(): Either<AppError, List<Entry>> {
        return db.use {
            val query = queryOf("select * from entry")
                .map(Entry.fromRow)
                .asList
            it.run(query)
        }
    }

    fun getUserEntries(userId: Int): Either<AppError, List<Entry>> {
        return db.use {
            val query = queryOf("select * from entry where user_id = ?", userId)
                .map(Entry.fromRow)
                .asList
            it.run(query)
        }
    }

    fun add(entry: NewEntry): Either<AppError, Unit> =
        db.use {
            val query = queryOf("""
                insert into entry(title, author, filename, compo_id, user_id)
                    values(?, ?, ?, ?, ?)
                """.trimIndent(),
                entry.title,
                entry.author,
                entry.file.name,
                entry.compoId,
                entry.userId,
            ).asUpdate
            it.run(query)
        }
}

data class Entry(
    val id: Int,
    val title: String,
    val author: String,
    val filename: String,
    val screenComment: Option<String>,
    val orgComment: Option<String>,
    val compoId: Int,
    val userId: Int,
    val timestamp: LocalDateTime,
) {
    companion object {
        val fromRow: (Row) -> Entry = { row ->
            Entry(
                row.int("id"),
                row.string("title"),
                row.string("author"),
                row.string("filename"),
                Option.fromNullable(row.stringOrNull("screen_comment")),
                Option.fromNullable(row.stringOrNull("org_comment")),
                row.int("compo_id"),
                row.int("user_id"),
                row.localDateTime("timestamp")
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
