package party.jml.partyboi.triggers

import arrow.core.Either
import arrow.core.flatten
import arrow.core.raise.either
import kotlinx.html.InputType
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.*
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Field
import party.jml.partyboi.signals.Signal
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.schedule

class TriggerRepository(val app: AppServices) : Logging() {
    private val db = app.db
    private val scheduler: TimerTask = Timer().schedule(0, 10000) {
        runScheduled(LocalDateTime.now())
    }

    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS trigger (
                id SERIAL PRIMARY KEY,
                type text NOT NULL,
                action jsonb NOT NULL,
                enabled boolean NOT NULL DEFAULT true,
                on_event integer REFERENCES event(id) ON DELETE CASCADE,
                on_signal text,            
                description text NOT NULL,
                execution_time timestamp with time zone,
                error text
            ) 
        """)
    }

    suspend fun subscribeToSignals() {
        app.signals.flow.collect { runSignaled(it) }
    }

    fun onEventStart(eventId: Int, action: Action, tx: TransactionalSession? = null): Either<AppError, TriggerRow> = db.use(tx) {
        either {
            it.one(
                queryOf("""
                INSERT INTO trigger (on_event, type, action, description)
                VALUES (?, ?, ?::jsonb, ?)
                RETURNING *
            """,
                    eventId,
                    action.javaClass.name,
                    action.toJson(),
                    action.description(app).bind(),
                ).map(TriggerRow.fromRow))
        }.flatten()
    }

    fun onSignal(signal: Signal, action: Action, tx: TransactionalSession? = null): Either<AppError, TriggerRow> = db.use(tx) {
        either {
            it.one(
                queryOf("""
                INSERT INTO trigger (on_signal, type, action, description)
                VALUES (?, ?, ?::jsonb, ?)
                RETURNING *
            """,
                    signal.toString(),
                    action.javaClass.name,
                    action.toJson(),
                    action.description(app).bind(),
                ).map(TriggerRow.fromRow))
        }.flatten()
    }

    fun setEnabled(triggerId: Int, enabled: Boolean) = db.use {
        it.updateOne(queryOf("UPDATE trigger SET enabled = ? WHERE id = ?", enabled, triggerId))
    }

    fun getTriggersForEvent(eventId: Int): Either<AppError, List<TriggerRow>> = db.use {
        it.many(
            queryOf("""
                SELECT *
                FROM trigger
                WHERE on_event = ?
                ORDER BY description
            """, eventId)
                .map(TriggerRow.fromRow)
        )
    }

    fun getTriggersForSignal(signal: Signal) = db.use {
        it.many(
            queryOf("""
                SELECT *
                FROM trigger
                WHERE on_signal = ?
                ORDER BY description
            """, signal.toString())
                .map(TriggerRow.fromRow)
        )
    }

    fun rescheduleEvent(eventId: Int, tx: TransactionalSession? = null): Either<AppError, Unit> = db.use(tx) {
        it.exec(queryOf("""
            UPDATE trigger
            SET execution_time = NULL
            WHERE on_event = ?
        """, eventId))
    }

    private fun setSuccessful(triggerId: Int, time: LocalDateTime) = db.use {
        it.updateOne(queryOf("UPDATE trigger SET execution_time = ? WHERE id = ?", time, triggerId))
    }

    private fun setFailed(triggerId: Int, time: LocalDateTime, error: String) = db.use {
        it.updateOne(queryOf("UPDATE trigger SET error = ?, execution_time = ? WHERE id = ?", error, time, triggerId))
    }

    private fun runScheduled(time: LocalDateTime): Either<AppError, Unit> = either {
        getScheduledTriggers(time).bind().forEach {
            executeTrigger(it, time)
        }
    }

    private fun runSignaled(signal: Signal): Either<AppError, Unit> = either {
        log.info("runSignaled: $signal")
        getSignaledTriggers(signal.toString()).bind().forEach {
            executeTrigger(it, LocalDateTime.now())
        }
    }

    private fun executeTrigger(trigger: TriggerRow, logTime: LocalDateTime): Either<AppError, Unit> =
        trigger.getAction().apply(app).fold(
            { error -> setFailed(trigger.triggerId, logTime, error.message) },
            { _ -> setSuccessful(trigger.triggerId, logTime) }
        )

    private fun getScheduledTriggers(now: LocalDateTime): Either<AppError, List<TriggerRow>> = db.use {
        it.many(
            queryOf("""
                SELECT *
                FROM trigger
                JOIN event ON event.id = trigger.on_event
                WHERE execution_time IS NULL
                  AND enabled
                  AND event.time <= ?
                """,
                now,
            ).map(TriggerRow.fromRow)
        )
    }

    private fun getSignaledTriggers(signal: String) = db.use {
        it.many(
            queryOf("""
                SELECT *
                FROM trigger
                WHERE execution_time IS NULL
                  AND enabled
                  AND on_signal = ?
                """,
                signal,
            ).map(TriggerRow.fromRow)
        )
    }

}

interface TriggerRow {
    val triggerId: Int
    val condition: TriggerCondition
    val type: String
    val actionJson: String
    val description: String
    val enabled: Boolean

    fun getAction(): Action = when (type) {
        OpenCloseVoting::class.qualifiedName -> Json.decodeFromString<OpenCloseVoting>(actionJson)
        OpenCloseSubmitting::class.qualifiedName -> Json.decodeFromString<OpenCloseSubmitting>(actionJson)
        OpenLiveVoting::class.qualifiedName -> Json.decodeFromString<OpenLiveVoting>(actionJson)
        CloseLiveVoting::class.qualifiedName -> Json.decodeFromString<CloseLiveVoting>(actionJson)
        EnableLiveVotingForEntry::class.qualifiedName -> Json.decodeFromString<EnableLiveVotingForEntry>(actionJson)
        else -> TODO("JSON decoding not implemented for $type")
    }

    companion object {
        val fromRow: (Row) -> TriggerRow = { row ->
            val triggerId = row.int("id")
            val condition = TriggerCondition(
                row.intOrNull("on_event"),
                row.stringOrNull("on_signal"))
            val type = row.string("type")
            val executionTime = row.localDateTimeOrNull("execution_time")
            val action = row.string("action")
            val error = row.stringOrNull("error")
            val description = row.string("description")
            val enabled = row.boolean("enabled")

            if (executionTime != null) {
                if (error != null) {
                    FailedTriggerRow(triggerId, condition, type, executionTime, action, error, description, enabled)
                } else {
                    SuccessfulTriggerRow(triggerId, condition, type, executionTime, action, description, enabled)
                }
            } else {
                PendingTriggerRow(triggerId, condition, type, action, description, enabled)
            }
        }
    }
}

data class TriggerCondition(
    val eventId: Int?,
    val signal: String?
)

data class PendingTriggerRow(
    override val triggerId: Int,
    override val condition: TriggerCondition,
    override val type: String,
    override val actionJson: String,
    override val description: String,
    override val enabled: Boolean,
) : TriggerRow

data class SuccessfulTriggerRow(
    override val triggerId: Int,
    override val condition: TriggerCondition,
    override val type: String,
    val executionTime: LocalDateTime,
    override val actionJson: String,
    override val description: String,
    override val enabled: Boolean,
) : TriggerRow

data class FailedTriggerRow(
    override val triggerId: Int,
    override val condition: TriggerCondition,
    override val type: String,
    val executionTime: LocalDateTime,
    override val actionJson: String,
    val error: String,
    override val description: String,
    override val enabled: Boolean,
) : TriggerRow

data class NewScheduledTrigger(
    @property:Field(type = InputType.hidden)
    val eventId: Int,
    @property:Field(1, "Action")
    val action: String,
    @property:Field(2, "Compo")
    val compoId: Int,
) : Validateable<NewScheduledTrigger> {

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