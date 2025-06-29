package party.jml.partyboi.schedule

import arrow.core.Option
import arrow.core.Some
import arrow.core.raise.either
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.db.*
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Label
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.*
import party.jml.partyboi.triggers.Action
import party.jml.partyboi.triggers.TriggerRow
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import kotlin.time.Duration.Companion.hours

class EventRepository(private val app: AppServices) : Logging() {
    private val db = app.db
    private val listener = EventSignalEmitter(app)

    suspend fun get(eventId: Int): AppResult<Event> = db.use {
        it.one(queryOf("SELECT * FROM event WHERE id = ?", eventId).map(Event.fromRow))
    }

    suspend fun getBetween(since: Instant, until: Instant): AppResult<List<Event>> = db.use {
        it.many(
            queryOf(
                "SELECT * FROM event WHERE time > ? AND time <= ? ORDER BY time",
                since,
                until
            ).map(Event.fromRow)
        )
    }

    suspend fun getAll(): AppResult<List<Event>> = db.use {
        it.many(queryOf("SELECT * FROM event ORDER BY time").map(Event.fromRow))
    }

    suspend fun getPublic(): AppResult<List<Event>> = db.use {
        it.many(queryOf("SELECT * FROM event WHERE visible ORDER BY time").map(Event.fromRow))
    }

    suspend fun add(event: NewEvent, tx: TransactionalSession? = null): AppResult<Event> = db.use(tx) {
        it.one(
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

    suspend fun add(event: NewEvent, actions: List<Action>): AppResult<Pair<Event, List<TriggerRow>>> =
        db.transaction { tx ->
            either {
                val createdEvent = add(event, tx).bind()
                val createdTriggers = actions
                    .map { app.triggers.add(createdEvent.signal(), it, tx) }
                    .bindAll()
                Pair(createdEvent, createdTriggers)
            }
        }

    suspend fun update(event: Event, tx: TransactionalSession? = null): AppResult<Event> = db.use(tx) {
        app.triggers.reset(event.signal(), tx)
        it.one(
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

    suspend fun delete(eventId: Int): AppResult<Unit> = db.use {
        it.updateOne(queryOf("DELETE FROM event WHERE id = ?", eventId))
    }

    suspend fun deleteAll(): AppResult<Unit> = db.use {
        it.exec(queryOf("DELETE FROM event"))
    }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.events.size} events")
        data.events.map {
            tx.exec(
                queryOf(
                    "INSERT INTO event (id, name, time, visible) VALUES (?, ?, ?, ?)",
                    it.id,
                    it.name,
                    it.startTime,
                    it.visible,
                )
            )
        }.bindAll()
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
        "endTime" to startTime.plus(1.hours).toIsoString()
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

        fun make(otherEvents: List<Event>, timeService: TimeService): NewEvent {
            return make(timeService.today(), otherEvents.map { it.endTime ?: it.startTime })
        }
    }
}

@Serializable
data class Event(
    @Field(presentation = FieldPresentation.hidden)
    val id: Int,
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
                id = row.int("id"),
                name = row.string("name"),
                startTime = row.instant("time").toKotlinInstant(),
                endTime = row.instantOrNull("end_time")?.toKotlinInstant(),
                visible = row.boolean("visible"),
            )
        }
    }
}
