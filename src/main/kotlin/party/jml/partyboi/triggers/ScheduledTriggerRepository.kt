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
import party.jml.partyboi.data.*
import party.jml.partyboi.form.DropdownOption
import party.jml.partyboi.form.Field
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.schedule

class ScheduledTriggerRepository(val app: AppServices) {
    private val db = app.db
    private var scheduler: TimerTask = Timer().schedule(0, 1000) {
        runScheduled(app, LocalDateTime.now())
    }

    fun init() {
        db.init("""
           CREATE TABLE IF NOT EXISTS scheduled_trigger (
                id SERIAL PRIMARY KEY,
                event_id integer NOT NULL REFERENCES event(id) ON DELETE CASCADE,
                type text NOT NULL,
                description text NOT NULL,
                enabled boolean NOT NULL DEFAULT true,
                execution_time timestamp with time zone,
                trigger jsonb NOT NULL,
                error text
            ) 
        """)
    }

    fun schedule(eventId: Int, trigger: Trigger, tx: TransactionalSession? = null): Either<AppError, ScheduledTriggerRow> = db.use(tx) {
        either {
            it.one(
                queryOf("""
                INSERT INTO scheduled_trigger (event_id, type, trigger, description)
                VALUES (?, ?, ?::jsonb, ?)
                RETURNING *
            """,
                    eventId,
                    trigger.javaClass.name,
                    trigger.toJson(),
                    trigger.description(app).bind(),
                ).map(ScheduledTriggerRow.fromRow))
        }.flatten()
    }

    fun setEnabled(triggerId: Int, enabled: Boolean) = db.use {
        it.updateOne(queryOf("UPDATE scheduled_trigger SET enabled = ? WHERE id = ?", enabled, triggerId))
    }

    fun getTriggersForEvent(eventId: Int): Either<AppError, List<ScheduledTriggerRow>> = db.use {
        it.many(
            queryOf("""
                SELECT *
                FROM scheduled_trigger
                WHERE event_id = ?
                ORDER BY description
            """, eventId)
                .map(ScheduledTriggerRow.fromRow)
        )
    }

    fun rescheduleEvent(eventId: Int, tx: TransactionalSession? = null): Either<AppError, Unit> = db.use(tx) {
        it.exec(queryOf("""
            UPDATE scheduled_trigger
            SET execution_time = NULL
            WHERE event_id = ?
        """, eventId))
    }

    private fun setSuccessful(triggerId: Int, time: LocalDateTime) = db.use {
        it.updateOne(queryOf("UPDATE scheduled_trigger SET execution_time = ? WHERE id = ?", time, triggerId))
    }

    private fun setFailed(triggerId: Int, time: LocalDateTime, error: String) = db.use {
        it.updateOne(queryOf("UPDATE scheduled_trigger SET error = ?, execution_time = ? WHERE id = ?", error, time, triggerId))
    }

    private fun runScheduled(app: AppServices, time: LocalDateTime) = either {
        getTriggersToRun(time).bind().forEach { trigger ->
            trigger.getTrigger().apply(app).fold(
                { error -> setFailed(trigger.triggerId, time, error.message) },
                { _ -> setSuccessful(trigger.triggerId, time) }
            )
        }
    }

    private fun getTriggersToRun(time: LocalDateTime): Either<AppError, List<ScheduledTriggerRow>> = db.use {
        it.many(
            queryOf("""
                SELECT *
                FROM scheduled_trigger
                JOIN event ON event.id = scheduled_trigger.event_id
                WHERE execution_time IS NULL
                  AND enabled
                  AND event.time <= ?
                """,
                time,
            ).map(ScheduledTriggerRow.fromRow)
        )
    }
}

interface ScheduledTriggerRow {
    val triggerId: Int
    val eventId: Int
    val type: String
    val triggerJson: String
    val description: String
    val enabled: Boolean

    fun getTrigger() = when (type) {
        CompoVotingTrigger::class.qualifiedName -> Json.decodeFromString<CompoVotingTrigger>(triggerJson)
        EntrySubmittingTrigger::class.qualifiedName -> Json.decodeFromString<EntrySubmittingTrigger>(triggerJson)
        else -> TODO("JSON decoding not implemented for $type")
    }

    companion object {
        val fromRow: (Row) -> ScheduledTriggerRow = { row ->
            val triggerId = row.int("id")
            val eventId = row.int("event_id")
            val type = row.string("type")
            val executionTime = row.localDateTimeOrNull("execution_time")
            val trigger = row.string("trigger")
            val error = row.stringOrNull("error")
            val description = row.string("description")
            val enabled = row.boolean("enabled")

            if (executionTime != null) {
                if (error != null) {
                    FailedTriggerRow(triggerId, eventId, type, executionTime, trigger, error, description, enabled)
                } else {
                    SuccessfulTriggerRow(triggerId, eventId, type, executionTime, trigger, description, enabled)
                }
            } else {
                PendingTriggerRow(triggerId, eventId, type, trigger, description, enabled)
            }
        }
    }
}

data class PendingTriggerRow(
    override val triggerId: Int,
    override val eventId: Int,
    override val type: String,
    override val triggerJson: String,
    override val description: String,
    override val enabled: Boolean,
) : ScheduledTriggerRow

data class SuccessfulTriggerRow(
    override val triggerId: Int,
    override val eventId: Int,
    override val type: String,
    val executionTime: LocalDateTime,
    override val triggerJson: String,
    override val description: String,
    override val enabled: Boolean,
) : ScheduledTriggerRow

data class FailedTriggerRow(
    override val triggerId: Int,
    override val eventId: Int,
    override val type: String,
    val executionTime: LocalDateTime,
    override val triggerJson: String,
    val error: String,
    override val description: String,
    override val enabled: Boolean,
) : ScheduledTriggerRow

data class NewScheduledTrigger(
    @property:Field(type = InputType.hidden)
    val eventId: Int,
    @property:Field(1, "Action")
    val action: String,
    @property:Field(2, "Compo")
    val compoId: Int,
) : Validateable<NewScheduledTrigger> {
    fun toTrigger() = Action.valueOf(action).getTrigger(this)

    companion object {
        fun empty(eventId: Int) = NewScheduledTrigger(eventId, "", -1)

        enum class Action(val getTrigger: (NewScheduledTrigger) -> Trigger) {
            VOTE_CLOSE({ CompoVotingTrigger(it.compoId, false) }),
            VOTE_OPEN({ CompoVotingTrigger(it.compoId, true) }),
            SUBMIT_CLOSE({ EntrySubmittingTrigger(it.compoId, false) }),
            SUBMIT_OPEN({ EntrySubmittingTrigger(it.compoId, true) }),
        }

        val TriggerOptions = listOf(
            DropdownOption(Action.VOTE_CLOSE.name, "Close voting"),
            DropdownOption(Action.VOTE_OPEN.name, "Open voting"),
            DropdownOption(Action.SUBMIT_CLOSE.name, "Close submitting"),
            DropdownOption(Action.SUBMIT_OPEN.name, "Open submitting"),
        )
    }
}