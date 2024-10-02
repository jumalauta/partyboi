package party.jml.partyboi.database

import arrow.core.Either
import arrow.core.Option
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.ValidationError
import party.jml.partyboi.form.Field

class CompoRepository(private val db: DatabasePool) {
    init {
        db.use {
            it.run(queryOf("""
                CREATE TABLE IF NOT EXISTS compo (
                    id SERIAL PRIMARY KEY,
                    name text NOT NULL,
                    rules text NOT NULL DEFAULT "",
                    allow_submit boolean NOT NULL DEFAULT true,
                    allow_vote boolean NOT NULL DEFAULT false
                );
            """.trimIndent()).asExecute)
        }
    }

    fun getAllCompos(): Either<AppError, List<Compo>> {
        return getRows("select * from compo order by name")
    }

    fun getOpenCompos(): Either<AppError, List<Compo>> {
        return getRows("select * from compo where allow_submit order by name")
    }

    fun add(compo: NewCompo): Either<AppError, Unit> =
        db.use {
            val query = queryOf("insert into compo(name, rules) values(?, ?)", compo.name, compo.rules).asExecute
            it.run(query)
        }

    fun allowSubmit(compoId: Int, state: Boolean): Either<AppError, Int> =
        db.use {
            it.run(queryOf("update compo set allow_submit = ? where id = ?", state, compoId).asUpdate)
        }

    fun allowVoting(compoId: Int, state: Boolean): Either<AppError, Int> =
        db.use {
            it.run(queryOf("update compo set allow_vote = ? where id = ?", state, compoId).asUpdate)
        }

    private fun getRows(queryStr: String): Either<AppError, List<Compo>> {
        return db.use {
            val query = queryOf(queryStr)
                .map(Compo.fromRow)
                .asList
            it.run(query)
        }

    }
}

data class Compo(
    val id: Int,
    val name: String,
    val rules: String,
    val allowSubmit: Boolean,
    val allowVote: Boolean,
) {
    companion object {
        val fromRow: (Row) -> Compo = { row ->
            Compo(
                id = row.int("id"),
                name = row.string("name"),
                rules = row.string("rules"),
                allowSubmit = row.boolean("allow_submit"),
                allowVote = row.boolean("allow_vote"),
            )
        }
    }
}

data class NewCompo(
    @property:Field(order = 0, label = "Name")
    val name: String,
    @property:Field(order = 1, label = "Description / rules", large = true)
    val rules: String,
) : Validateable<NewCompo> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64),
    )

    companion object {
        val Empty = NewCompo("", "")
    }
}