package party.jml.partyboi.triggers

import arrow.core.flatten
import arrow.core.raise.either
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.db.*
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.validation.Validateable

class TriggerRepository(app: AppServices) : Service(app) {
    private val db = app.db

    suspend fun start() {
        app.signals.flow.collect { execute(it) }
    }

    suspend fun add(signal: Signal, action: Action, tx: TransactionalSession? = null): AppResult<TriggerRow> =
        db.use(tx) {
            either {
                it.one(
                    queryOf(
                        """
                INSERT INTO trigger (signal, type, action, description)
                VALUES (?, ?, ?::jsonb, ?)
                RETURNING *
            """,
                        signal.toString(),
                        action.javaClass.name,
                        action.toJson(),
                        action.description(app).bind(),
                    ).map(TriggerRow.fromRow)
                )
            }.flatten()
        }

    suspend fun setEnabled(triggerId: Int, enabled: Boolean) = db.use {
        it.updateOne(queryOf("UPDATE trigger SET enabled = ? WHERE id = ?", enabled, triggerId))
    }

    suspend fun getTriggersForSignal(signal: Signal) = db.use {
        it.many(
            queryOf(
                """
                SELECT *
                FROM trigger
                WHERE signal = ?
                ORDER BY description
            """, signal.toString()
            )
                .map(TriggerRow.fromRow)
        )
    }

    suspend fun getAllTriggers() = db.use {
        it.many(queryOf("SELECT * FROM trigger").map(TriggerRow.fromRow))
    }

    suspend fun reset(signal: Signal, tx: TransactionalSession? = null): AppResult<Unit> = db.use(tx) {
        it.exec(
            queryOf(
                """
            UPDATE trigger
            SET executed_at = NULL
            WHERE signal = ?
        """, signal.toString()
            )
        )
    }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.triggers.size} triggers")
        data.triggers.map {
            tx.exec(
                queryOf(
                    "INSERT INTO trigger (id, type, action, enabled, signal, description) VALUES (?, ?, ?::jsonb, ?, ?, ?)",
                    it.triggerId,
                    it.triggerType,
                    it.actionJson,
                    it.enabled,
                    it.signal,
                    it.description,
                    // TODO: Copy execution time and error
                )
            )
        }.bindAll()
    }

    private suspend fun setSuccessful(triggerId: Int, time: Instant) = db.use {
        it.updateOne(queryOf("UPDATE trigger SET executed_at = ? WHERE id = ?", time, triggerId))
    }

    private suspend fun setFailed(triggerId: Int, time: Instant, error: String) = db.use {
        it.updateOne(queryOf("UPDATE trigger SET error = ?, executed_at = ? WHERE id = ?", error, time, triggerId))
    }

    private suspend fun execute(signal: Signal): AppResult<Unit> = either {
        val now = Clock.System.now()
        getTriggers(signal.toString()).bind().forEach {
            val result = executeTrigger(it, now)
            log.info(
                "Executed trigger {} -> {} -> {}",
                signal,
                it.description,
                result.fold({ "failed: ${it.message}" }, { "ok" })
            )
        }
    }

    private suspend fun executeTrigger(trigger: TriggerRow, logTime: Instant): AppResult<Unit> {
        return trigger.getAction().apply(app).fold(
            { error -> setFailed(trigger.triggerId, logTime, error.message) },
            { _ -> setSuccessful(trigger.triggerId, logTime) }
        )
    }

    private suspend fun getTriggers(signal: String) = db.use {
        it.many(
            queryOf(
                """
                SELECT *
                FROM trigger
                WHERE executed_at IS NULL
                  AND enabled
                  AND signal = ?
                """,
                signal,
            ).map(TriggerRow.fromRow)
        )
    }

}

@Serializable
sealed class TriggerRow {
    abstract val triggerId: Int
    abstract val signal: String

    abstract val triggerType: String
    abstract val actionJson: String
    abstract val description: String
    abstract val enabled: Boolean

    fun getAction(): Action = when (triggerType) {
        OpenCloseVoting::class.qualifiedName -> Json.decodeFromString<OpenCloseVoting>(actionJson)
        OpenCloseSubmitting::class.qualifiedName -> Json.decodeFromString<OpenCloseSubmitting>(actionJson)
        OpenLiveVoting::class.qualifiedName -> Json.decodeFromString<OpenLiveVoting>(actionJson)
        CloseLiveVoting::class.qualifiedName -> Json.decodeFromString<CloseLiveVoting>(actionJson)
        EnableLiveVotingForEntry::class.qualifiedName -> Json.decodeFromString<EnableLiveVotingForEntry>(actionJson)
        else -> TODO("JSON decoding not implemented for $triggerType")
    }

    companion object {
        val fromRow: (Row) -> TriggerRow = { row ->
            val triggerId = row.int("id")
            val signal = row.string("signal")
            val type = row.string("type")
            val executionTime = row.instantOrNull("executed_at")?.toKotlinInstant()
            val action = row.string("action")
            val error = row.stringOrNull("error")
            val description = row.string("description")
            val enabled = row.boolean("enabled")

            if (executionTime != null) {
                if (error != null) {
                    FailedTriggerRow(
                        triggerId,
                        signal,
                        type,
                        executionTime,
                        action,
                        error,
                        description,
                        enabled
                    )
                } else {
                    SuccessfulTriggerRow(
                        triggerId,
                        signal,
                        type,
                        executionTime,
                        action,
                        description,
                        enabled
                    )
                }
            } else {
                PendingTriggerRow(triggerId, signal, type, action, description, enabled)
            }
        }
    }
}

@Serializable
data class PendingTriggerRow(
    override val triggerId: Int,
    override val signal: String,
    override val triggerType: String,
    override val actionJson: String,
    override val description: String,
    override val enabled: Boolean,
) : TriggerRow()

@Serializable
data class SuccessfulTriggerRow(
    override val triggerId: Int,
    override val signal: String,
    override val triggerType: String,
    val executionTime: Instant,
    override val actionJson: String,
    override val description: String,
    override val enabled: Boolean,
) : TriggerRow()

@Serializable
data class FailedTriggerRow(
    override val triggerId: Int,
    override val signal: String,
    override val triggerType: String,
    val executionTime: Instant,
    override val actionJson: String,
    val error: String,
    override val description: String,
    override val enabled: Boolean,
) : TriggerRow()

data class NewScheduledTrigger(
    @Field(presentation = FieldPresentation.hidden)
    val eventId: Int,
    @Field("Action")
    val action: String,
    @Field("Compo")
    val compoId: Int,
) : Validateable<NewScheduledTrigger> {

    fun signal(): Signal = Signal.eventStarted(eventId)
    fun toAction() = Action.valueOf(action).getAction(this)

    companion object {
        fun empty(eventId: Int) = NewScheduledTrigger(eventId, "", -1)

        enum class Action(val getAction: (NewScheduledTrigger) -> party.jml.partyboi.triggers.Action) {
            VOTE_CLOSE({ OpenCloseVoting(it.compoId, false) }),
            VOTE_OPEN({ OpenCloseVoting(it.compoId, true) }),
            SUBMIT_CLOSE({ OpenCloseSubmitting(it.compoId, false) }),
            SUBMIT_OPEN({ OpenCloseSubmitting(it.compoId, true) }),
        }

        val TriggerOptions = listOf(
            DropdownOption(Action.VOTE_CLOSE.name, "Close voting"),
            DropdownOption(Action.VOTE_OPEN.name, "Open voting"),
            DropdownOption(Action.SUBMIT_CLOSE.name, "Close submitting"),
            DropdownOption(Action.SUBMIT_OPEN.name, "Open submitting"),
        )
    }
}