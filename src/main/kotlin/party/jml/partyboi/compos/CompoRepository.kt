package party.jml.partyboi.compos

import arrow.core.*
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.auth.User
import party.jml.partyboi.data.*
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.entries.FileFormat
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.DropdownOptionSupport
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.templates.NavItem

class CompoRepository(private val app: AppServices) : Logging() {
    private val db = app.db
    private val GENERAL_RULES = "CompoRepository.GeneralRules"

    fun getGeneralRules(): Either<AppError, GeneralRules> =
        app.properties.getOrElse(GENERAL_RULES, "")
            .flatMap { it.string() }
            .map { GeneralRules(it) }

    fun setGeneralRules(rules: GeneralRules): Either<AppError, Unit> =
        app.properties.set(GENERAL_RULES, rules.rules)

    fun getById(id: Int, tx: TransactionalSession? = null): Either<AppError, Compo> = db.use(tx) {
        it.one(queryOf("select * from compo where id = ?", id).map(Compo.fromRow))
    }

    fun getAllCompos(): Either<AppError, List<Compo>> = db.use {
        it.many(queryOf("select * from compo order by name").map(Compo.fromRow))
    }

    fun getOpenCompos(): Either<AppError, List<Compo>> = db.use {
        it.many(queryOf("select * from compo where allow_submit and visible order by name").map(Compo.fromRow))
    }

    fun add(compo: NewCompo): Either<AppError, Unit> = db.use {
        it.exec(queryOf("insert into compo(name, rules, visible) values(?, ?, false)", compo.name, compo.rules))
    }

    fun update(compo: Compo): Either<AppError, Unit> =
        db.use {
            it.updateOne(
                queryOf(
                    """
                UPDATE compo
                SET
                    name = ?, 
                    rules = ?,
                    formats = ?
                WHERE id = ?
                """,
                    compo.name,
                    compo.rules,
                    compo.fileFormats.map { it.name }.toTypedArray(),
                    compo.id,
                )
            )
        }.onRight {
            runBlocking { app.signals.emit(Signal.compoContentUpdated(compo.id)) }
        }


    fun setVisible(compoId: Int, state: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("update compo set visible = ? where id = ?", state, compoId))
    }

    fun allowSubmit(compoId: Int, state: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(
            queryOf(
                "update compo set allow_submit = ? where id = ? and (not ? or not allow_vote)",
                state,
                compoId,
                state
            )
        )
    }

    fun allowVoting(compoId: Int, state: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(
            queryOf(
                "update compo set allow_vote = ? where id = ? and (not ? or not allow_submit)",
                state,
                compoId,
                state
            )
        ).onRight {
            runBlocking {
                app.signals.emit(if (state) Signal.votingOpened(compoId) else Signal.votingClosed(compoId))
            }
        }
    }

    fun publishResults(compoId: Int, state: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(
            queryOf(
                "update compo set public_results = ? where id = ?",
                state,
                compoId,
            )
        )
    }

    fun isVotingOpen(compoId: Int): Either<AppError, Boolean> = db.use {
        it.one(queryOf("SELECT allow_vote FROM compo WHERE id = ?", compoId).map(asBoolean))
    }

    fun assertCanSubmit(compoId: Int, isAdmin: Boolean): Either<AppError, Unit> = db.use {
        it.one(
            queryOf(
                "select ? or (visible and allow_submit) from compo where id = ?",
                isAdmin,
                compoId,
            ).map { it.boolean(1) })
            .flatMap { if (it) Unit.right() else Forbidden().left() }
    }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.compos.size} compos")
        data.compos.map {
            tx.exec(
                queryOf(
                    "INSERT INTO compo (id, name, rules, visible, allow_submit, allow_vote, public_results, formats) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    it.id,
                    it.name,
                    it.rules,
                    it.visible,
                    it.allowSubmit,
                    it.allowVote,
                    it.publicResults,
                    it.fileFormats.map { it.name }.toTypedArray(),
                )
            )
        }.bindAll()
    }
}

@Serializable
data class Compo(
    @property:Field(presentation = FieldPresentation.hidden)
    val id: Int,
    @property:Field(order = 0, label = "Name")
    val name: String,
    @property:Field(order = 1, label = "Description / rules", presentation = FieldPresentation.large)
    val rules: String,
    val visible: Boolean,
    val allowSubmit: Boolean,
    val allowVote: Boolean,
    val publicResults: Boolean,
    @property:Field(presentation = FieldPresentation.custom)
    val requireFile: Boolean?,
    @property:Field(presentation = FieldPresentation.custom)
    val fileFormats: List<FileFormat>,
) : Validateable<Compo>, DropdownOptionSupport {
    fun canSubmit(user: User): Boolean = user.isAdmin || (visible && allowSubmit)

    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64),
    )

    override fun toDropdownOption() = DropdownOption(
        value = id.toString(),
        label = name,
        dataFields = mapOf(
            "uploadEnabled" to if (requireFile == false) null else "true",
            "accept" to acceptedFormatsString(),
        ),
    )

    fun toNavItem() = NavItem("/admin/compos/${id}", name)

    fun acceptedFormatsString() =
        fileFormats
            .flatMap { it.extensions.map { ext -> ".$ext" } + it.mimeTypes }
            .joinToString(",")

    companion object {
        val fromRow: (Row) -> Compo = { row ->
            Compo(
                id = row.int("id"),
                name = row.string("name"),
                rules = row.string("rules"),
                visible = row.boolean("visible"),
                allowSubmit = row.boolean("allow_submit"),
                allowVote = row.boolean("allow_vote"),
                publicResults = row.boolean("public_results"),
                fileFormats = row.arrayOrNull<String>("formats")?.map { FileFormat.valueOf(it) } ?: emptyList(),
                requireFile = row.optionalBoolean("require_file"),
            )
        }

        val Empty = Compo(
            id = -1,
            name = "",
            rules = "",
            visible = false,
            allowSubmit = false,
            allowVote = false,
            publicResults = false,
            fileFormats = emptyList(),
            requireFile = null,
        )
    }
}

data class NewCompo(
    @property:Field(order = 0, label = "Name")
    val name: String,
    @property:Field(order = 1, label = "Description / rules", presentation = FieldPresentation.large)
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

data class GeneralRules(
    @property:Field(label = "General compo rules", presentation = FieldPresentation.large)
    val rules: String
) : Validateable<GeneralRules>
