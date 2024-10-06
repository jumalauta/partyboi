package party.jml.partyboi.database

import arrow.core.*
import kotlinx.html.InputType
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.errors.Forbidden
import party.jml.partyboi.errors.ValidationError
import party.jml.partyboi.form.Field

class CompoRepository(private val db: DatabasePool) {
    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS compo (
                id SERIAL PRIMARY KEY,
                name text NOT NULL,
                rules text NOT NULL DEFAULT '',
                visible boolean NOT NULL DEFAULT true,
                allow_submit boolean NOT NULL DEFAULT true,
                allow_vote boolean NOT NULL DEFAULT false
            );
        """)
    }

    fun getById(id: Int, tx: TransactionalSession? = null): Either<AppError, Compo> =
        db.use(tx).one(queryOf("select * from compo where id = ?", id).map(Compo.fromRow))

    fun getAllCompos(): Either<AppError, List<Compo>> =
        db.use().many(queryOf("select * from compo order by name").map(Compo.fromRow))

    fun getOpenCompos(): Either<AppError, List<Compo>> =
        db.use().many(queryOf("select * from compo where allow_submit and visible order by name").map(Compo.fromRow))

    fun add(compo: NewCompo): Either<AppError, Unit> =
        db.use().execAlways(queryOf("insert into compo(name, rules) values(?, ?)", compo.name, compo.rules))

    fun update(compo: Compo): Either<AppError, Unit> =
        db.use().updateOne(queryOf("update compo set name = ?, rules = ? where id = ?", compo.name, compo.rules, compo.id))

    fun setVisible(compoId: Int, state: Boolean): Either<AppError, Unit> =
        db.use().updateOne(queryOf("update compo set visible = ? where id = ?", state, compoId))

    fun allowSubmit(compoId: Int, state: Boolean): Either<AppError, Unit> =
        db.use().updateOne(queryOf("update compo set allow_submit = ? where id = ? and (not ? or not allow_vote)", state, compoId, state))

    fun allowVoting(compoId: Int, state: Boolean): Either<AppError, Unit> =
        db.use().updateOne(queryOf("update compo set allow_vote = ? where id = ? and (not ? or not allow_submit)", state, compoId, state))

    fun assertCanSubmit(compoId: Int, isAdmin: Boolean): Either<AppError, Unit> =
        db.use().one(queryOf(
            "select ? or (visible and allow_submit) from compo where id = ?",
            isAdmin,
            compoId,
        ).map { it.boolean(1) })
            .flatMap { if (it) Unit.right() else Forbidden().left() }
}

data class Compo(
    @property:Field(type = InputType.hidden)
    val id: Int,
    @property:Field(order = 0, label = "Name")
    val name: String,
    @property:Field(order = 1, label = "Description / rules", large = true)
    val rules: String,
    val visible: Boolean,
    val allowSubmit: Boolean,
    val allowVote: Boolean,
) : Validateable<Compo> {
    fun canSubmit(user: User): Boolean = user.isAdmin || (visible && allowSubmit)

    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64),
    )

    companion object {
        val fromRow: (Row) -> Compo = { row ->
            Compo(
                id = row.int("id"),
                name = row.string("name"),
                rules = row.string("rules"),
                visible = row.boolean("visible"),
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
