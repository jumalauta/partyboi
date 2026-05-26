package party.jml.partyboi.schedule

import arrow.core.Option
import arrow.core.Some
import arrow.core.raise.either
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.db.*
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Label
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.*
import party.jml.partyboi.triggers.Action
import party.jml.partyboi.triggers.TriggerRow
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

// When shifting following events, a gap this long or longer between consecutive
// events stops the cascade (it's treated as a natural break that absorbs the delay).
private val CASCADE_GAP_LIMIT = 3.hours

interface EventRepository {
    suspend fun get(eventId: UUID): AppResult<Event>
    suspend fun getBetween(since: Instant, until: Instant): AppResult<List<Event>>
    suspend fun getAll(): AppResult<List<Event>>
    suspend fun getPublic(): AppResult<List<Event>>
    suspend fun add(event: NewEvent, tx: TransactionalSession? = null): AppResult<Event>
    suspend fun add(event: NewEvent, actions: List<Action>): AppResult<Pair<Event, List<TriggerRow>>>
    suspend fun update(event: Event, tx: TransactionalSession? = null): AppResult<Event>
    suspend fun setName(eventId: UUID, name: String): AppResult<Unit>
    suspend fun setVisible(eventId: UUID, visible: Boolean): AppResult<Unit>
    suspend fun setStartTime(eventId: UUID, startTime: Instant): AppResult<Unit>
    suspend fun setEndTime(eventId: UUID, endTime: Instant?): AppResult<Unit>
    suspend fun nudge(eventId: UUID, delta: Duration): AppResult<Unit>
    suspend fun shiftFrom(threshold: Instant, delta: Duration): AppResult<Unit>
    suspend fun delete(eventId: UUID): AppResult<Unit>
    suspend fun deleteAll(): AppResult<Unit>
}

class EventRepositoryImpl(app: AppServices) : EventRepository, Service(app) {
    private val db = app.db

    override suspend fun get(eventId: UUID): AppResult<Event> = db.use {
        one(queryOf("SELECT * FROM event WHERE id = ?", eventId).map(Event.fromRow))
    }

    override suspend fun getBetween(since: Instant, until: Instant): AppResult<List<Event>> = db.use {
        many(
            queryOf(
                "SELECT * FROM event WHERE time > ? AND time <= ? ORDER BY time",
                since,
                until
            ).map(Event.fromRow)
        )
    }

    override suspend fun getAll(): AppResult<List<Event>> = db.use {
        many(queryOf("SELECT * FROM event ORDER BY time").map(Event.fromRow))
    }

    override suspend fun getPublic(): AppResult<List<Event>> = db.use {
        many(queryOf("SELECT * FROM event WHERE visible ORDER BY time").map(Event.fromRow))
    }

    override suspend fun add(event: NewEvent, tx: TransactionalSession?): AppResult<Event> = db.use(tx) {
        one(
            queryOf(
                """
            INSERT INTO event (name, time, end_time, visible) 
            VALUES (?, ?, ?, ?) 
            RETURNING *""",
                event.name,
                event.startTime,
                event.endTime,
                event.visible,
            ).map(Event.fromRow)
        )
    }

    override suspend fun add(event: NewEvent, actions: List<Action>): AppResult<Pair<Event, List<TriggerRow>>> =
        db.transaction {
            either {
                val createdEvent = add(event, this@transaction).bind()
                val createdTriggers = actions
                    .map { app.triggers.add(createdEvent.signal(), it, this@transaction) }
                    .bindAll()
                Pair(createdEvent, createdTriggers)
            }
        }

    override suspend fun update(event: Event, tx: TransactionalSession?): AppResult<Event> = db.use(tx) {
        app.triggers.reset(event.signal(), tx)
        one(
            queryOf(
                """
            UPDATE event
            SET
                name = ?,
                time = ?,
                end_time = ?,
                visible = ?
            WHERE id = ?
            RETURNING *""",
                event.name,
                event.startTime,
                event.endTime,
                event.visible,
                event.id,
            ).map(Event.fromRow)
        )
    }

    override suspend fun setName(eventId: UUID, name: String): AppResult<Unit> = db.use {
        updateOne(queryOf("UPDATE event SET name = ? WHERE id = ?", name, eventId))
    }

    override suspend fun setVisible(eventId: UUID, visible: Boolean): AppResult<Unit> = db.use {
        updateOne(queryOf("UPDATE event SET visible = ? WHERE id = ?", visible, eventId))
    }

    // Changing an event's start time re-arms its triggers (an event that moved should fire again).
    override suspend fun setStartTime(eventId: UUID, startTime: Instant): AppResult<Unit> = db.transaction {
        either {
            app.triggers.reset(Signal.eventStarted(eventId), this@transaction).bind()
            updateOne(queryOf("UPDATE event SET time = ? WHERE id = ?", startTime, eventId)).bind()
        }
    }

    // End time does not affect triggers (they fire on start time), so no reset needed.
    override suspend fun setEndTime(eventId: UUID, endTime: Instant?): AppResult<Unit> = db.use {
        updateOne(queryOf("UPDATE event SET end_time = ? WHERE id = ?", endTime, eventId))
    }

    // Shift a single event by delta, preserving its duration.
    override suspend fun nudge(eventId: UUID, delta: Duration): AppResult<Unit> = db.transaction {
        either {
            val event = one(queryOf("SELECT * FROM event WHERE id = ?", eventId).map(Event.fromRow)).bind()
            app.triggers.reset(event.signal(), this@transaction).bind()
            updateOne(
                queryOf(
                    "UPDATE event SET time = ?, end_time = ? WHERE id = ?",
                    event.startTime + delta,
                    event.endTime?.plus(delta),
                    event.id,
                )
            ).bind()
        }
    }

    // Shift the threshold event and the run of events that follow it by delta (e.g.
    // "the party is running late"). The cascade stops at the first gap of three hours
    // or more between consecutive events: such a gap is a natural break (meal, sleep,
    // next day's program) that absorbs the delay, so events beyond it stay put.
    override suspend fun shiftFrom(threshold: Instant, delta: Duration): AppResult<Unit> = db.transaction {
        either {
            val affected = many(
                queryOf("SELECT * FROM event WHERE time >= ? ORDER BY time", threshold).map(Event.fromRow)
            ).bind()
            var previous: Event? = null
            for (event in affected) {
                previous?.let { prev ->
                    val gap = event.startTime - (prev.endTime ?: prev.startTime)
                    if (gap >= CASCADE_GAP_LIMIT) return@either
                }
                app.triggers.reset(event.signal(), this@transaction).bind()
                updateOne(
                    queryOf(
                        "UPDATE event SET time = ?, end_time = ? WHERE id = ?",
                        event.startTime + delta,
                        event.endTime?.plus(delta),
                        event.id,
                    )
                ).bind()
                previous = event
            }
        }
    }

    override suspend fun delete(eventId: UUID): AppResult<Unit> = db.use {
        updateOne(queryOf("DELETE FROM event WHERE id = ?", eventId))
    }

    override suspend fun deleteAll(): AppResult<Unit> = db.use {
        exec(queryOf("DELETE FROM event"))
    }
}

interface ValidateableEvent<T : Validateable<T>> : Validateable<T> {
    val startTime: Instant
    val endTime: Instant?

    override fun validationErrors(): List<Option<ValidationError.Message>> =
        listOfNotNull(
            endTime?.let {
                if (it < startTime) Some(
                    ValidationError.Message(
                        "endTime",
                        "End time must be after start time",
                        endTime.toString()
                    )
                )
                else null
            }
        )

    override fun suggestedValues(): Map<String, String> = mapOf(
        "endTime" to startTime.plus(1.hours).toLocalIsoString()
    )
}

data class NewEvent(
    @Label("Event name")
    @NotEmpty
    val name: String,
    @Label("Start time and date")
    override val startTime: Instant,
    @Label("End time and date")
    override val endTime: Instant? = null,
    @Label("Show in public schedule")
    val visible: Boolean,
) : ValidateableEvent<NewEvent> {
    companion object {
        fun make(today: LocalDate, otherEventTimes: List<Instant>): NewEvent {
            val time = otherEventTimes.filter { it.toDate() == today }.maxOrNull()
                ?: otherEventTimes.maxOrNull()
                ?: today.atTime(12, 0).toInstant(TimeService.timeZone())
            return NewEvent(
                name = "",
                startTime = time,
                endTime = null,
                visible = true
            )
        }

        suspend fun make(otherEvents: List<Event>, timeService: TimeService): NewEvent {
            return make(timeService.today(), otherEvents.map { it.endTime ?: it.startTime })
        }
    }
}

@Serializable
data class Event(
    @Field(presentation = FieldPresentation.hidden)
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Label("Event name")
    @NotEmpty
    val name: String,
    @Label("Start time and date")
    override val startTime: Instant,
    @Label("End time and date")
    override val endTime: Instant?,
    @Label("Show in public schedule")
    val visible: Boolean,
) : ValidateableEvent<Event> {
    fun signal(): Signal = Signal.eventStarted(id)

    fun formatTime(timeZone: TimeZone): String =
        listOfNotNull(startTime, endTime)
            .map { it.toLocalDateTime(timeZone) }
            .joinToString("-") {
                it.displayTime()
            }

    companion object {
        val fromRow: (Row) -> Event = { row ->
            Event(
                id = row.uuid("id"),
                name = row.string("name"),
                startTime = row.instant("time").toKotlinInstant(),
                endTime = row.instantOrNull("end_time")?.toKotlinInstant(),
                visible = row.boolean("visible"),
            )
        }
    }
}
