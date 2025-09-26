package party.jml.partyboi.compos

import arrow.core.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.auth.User
import party.jml.partyboi.data.*
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.entries.FileFormat
import party.jml.partyboi.form.*
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.NavItem
import party.jml.partyboi.validation.MaxLength
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import java.util.*

class CompoRepository(app: AppServices) : Service(app) {
    private val db = app.db

    val generalRules = app.properties.createPersistentCachedValue("CompoRepository.GeneralRules", GeneralRules(""))

    suspend fun getById(id: UUID, tx: TransactionalSession? = null): AppResult<Compo> = db.use(tx) {
        it.one(queryOf("select * from compo where id = ?", id).map(Compo.fromRow))
    }

    suspend fun getAllCompos(): AppResult<List<Compo>> = db.use {
        it.many(queryOf("select * from compo order by name").map(Compo.fromRow))
    }

    suspend fun add(compo: NewCompo): AppResult<Compo> = db.use {
        it.one(
            queryOf(
                "insert into compo(name, rules, visible) values(?, ?, false) returning *",
                compo.name,
                compo.rules
            ).map(Compo.fromRow)
        )
    }

    suspend fun update(compo: Compo): AppResult<Unit> =
        db.use {
            it.updateOne(
                queryOf(
                    """
                UPDATE compo
                SET
                    name = ?, 
                    rules = ?,
                    formats = ?,
                    require_file = ?::boolean
                WHERE id = ?
                """,
                    compo.name,
                    compo.rules,
                    compo.fileFormats.map { it.name }.toTypedArray(),
                    compo.requireFile.toDatabaseEnum(),
                    compo.id,
                )
            )
        }.onRight {
            app.signals.emit(Signal.compoContentUpdated(compo.id, app.time))
        }


    suspend fun setVisible(compoId: UUID, state: Boolean): AppResult<Unit> = db.use {
        it.updateOne(queryOf("update compo set visible = ? where id = ?", state, compoId))
    }

    suspend fun allowSubmit(compoId: UUID, state: Boolean): AppResult<Unit> = db.use {
        it.updateOne(
            queryOf(
                "update compo set allow_submit = ? where id = ? and (not ? or not allow_vote)",
                state,
                compoId,
                state
            )
        )
    }

    suspend fun allowVoting(compoId: UUID, state: Boolean): AppResult<Unit> = db.use {
        it.updateOne(
            queryOf(
                "update compo set allow_vote = ? where id = ? and (not ? or not allow_submit)",
                state,
                compoId,
                state
            )
        ).onRight {
            app.signals.emit(if (state) Signal.votingOpened(compoId) else Signal.votingClosed(compoId))
        }
    }

    suspend fun publishResults(compoId: UUID, state: Boolean): AppResult<Unit> = db.use {
        it.updateOne(
            queryOf(
                "update compo set public_results = ? where id = ?",
                state,
                compoId,
            )
        )
    }

    suspend fun isVotingOpen(compoId: UUID): AppResult<Boolean> = db.use {
        it.one(queryOf("SELECT allow_vote FROM compo WHERE id = ?", compoId).map(asBoolean))
    }

    suspend fun assertCanSubmit(compoId: UUID, isAdmin: Boolean): AppResult<Unit> = db.use {
        it.one(
            queryOf(
                "select ? or (visible and allow_submit) from compo where id = ?",
                isAdmin,
                compoId,
            ).map { it.boolean(1) })
            .flatMap { if (it) Unit.right() else Forbidden().left() }
    }

    suspend fun deleteAll(): AppResult<Unit> =
        app.entries.deleteAll().flatMap {
            db.use { it.exec(queryOf("DELETE FROM compo")) }
        }
}

@Serializable
data class Compo(
    @Hidden
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Label("Name")
    @NotEmpty
    @MaxLength(64)
    val name: String,
    @Label("Description / rules")
    @Large
    val rules: String,
    val visible: Boolean,
    val allowSubmit: Boolean,
    val allowVote: Boolean,
    val publicResults: Boolean,
    @Custom
    @Contextual
    val requireFile: Option<Boolean>,
    @Custom
    val fileFormats: List<FileFormat>,
) : Validateable<Compo>, DropdownOptionSupport {
    fun canSubmit(user: User): Boolean = user.isAdmin || (visible && allowSubmit)

    override fun toDropdownOption() = DropdownOption(
        value = id.toString(),
        label = name,
        dataFields = mapOf(
            "uploadEnabled" to if (requireFile.isFalse()) null else "true",
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
                id = row.uuid("id"),
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
            id = UUIDv7.Empty,
            name = "",
            rules = "",
            visible = false,
            allowSubmit = false,
            allowVote = false,
            publicResults = false,
            fileFormats = emptyList(),
            requireFile = none(),
        )
    }
}

data class NewCompo(
    @Label("Name")
    @NotEmpty
    @MaxLength(64)
    val name: String,

    @Field(label = "Description / rules", presentation = FieldPresentation.large)
    val rules: String,
) : Validateable<NewCompo> {
    companion object {
        val Empty = NewCompo("", "")
    }
}

@Serializable
data class GeneralRules(
    @Field(label = "General compo rules", presentation = FieldPresentation.large)
    val rules: String
) : Validateable<GeneralRules>
