package party.jml.partyboi.schedule

import arrow.core.Either
import arrow.core.Option
import arrow.core.flatMap
import arrow.core.raise.either
import kotlinx.html.InputType
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.*
import party.jml.partyboi.form.Field
import party.jml.partyboi.triggers.ScheduledTriggerRow
import party.jml.partyboi.triggers.Trigger
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

class EventRepository(private val app: AppServices) {
    private val db = app.db

    fun init() {
        db.init("""
           CREATE TABLE event (
                id integer DEFAULT nextval('schedule_id_seq'::regclass) PRIMARY KEY,
                name text NOT NULL,
                time timestamp with time zone NOT NULL,
                visible boolean NOT NULL DEFAULT true
            ) 
        """)
    }

    fun get(eventId: Int): Either<AppError, Event> = db.use {
        it.one(queryOf("SELECT * FROM event WHERE id = ?", eventId).map(Event.fromRow))
    }

    fun getAll(): Either<AppError, List<Event>> = db.use {
        it.many(queryOf("SELECT * FROM event ORDER BY time").map(Event.fromRow))
    }

    fun getPublic(): Either<AppError, List<Event>> = db.use {
        it.many(queryOf("SELECT * FROM event WHERE visible ORDER BY time").map(Event.fromRow))
    }

    fun add(event: NewEvent, tx: TransactionalSession? = null): Either<AppError, Event> = db.use(tx) {
        it.one(queryOf("""
            INSERT INTO event (name, time, visible) 
            VALUES (?, ?, ?) 
            RETURNING *""",
            event.name,
            Timestamp.valueOf(event.time),
            event.visible,
        ).map(Event.fromRow))
    }

    fun add(event: NewEvent, triggers: List<Trigger>): Either<AppError, Pair<Event, List<ScheduledTriggerRow>>> =
        db.transaction { tx -> either {
            val createdEvent = add(event, tx).bind()
            val createdTriggers = triggers
                .map { app.triggers.schedule(createdEvent.id, it, tx) }
                .bindAll()
            Pair(createdEvent, createdTriggers)
        } }

    fun update(event: Event, tx: TransactionalSession? = null): Either<AppError, Event> = db.use(tx) {
        app.triggers.rescheduleEvent(event.id)
        it.one(queryOf("""
            UPDATE event
            SET
                name = ?,
                time = ?,
                visible = ?
            WHERE id = ?
            RETURNING *""",
            event.name,
            Timestamp.valueOf(event.time),
            event.visible,
            event.id,
        ).map(Event.fromRow))
    }

    fun delete(eventId: Int): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("DELETE FROM event WHERE id = ?", eventId))
    }
}

data class NewEvent(
    @property:Field(order = 0, label = "Event name")
    val name: String,
    @property:Field(order = 1, label = "Time and date", type = InputType.dateTimeLocal)
    val time: LocalDateTime,
    @property:Field(order = 2, label = "Show in public schedule", type = InputType.checkBox)
    val visible: Boolean,
) : Validateable<NewEvent> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
    )

    companion object {
        fun make(today: LocalDate, preferredDates: List<LocalDate>): NewEvent {
            val date = preferredDates.find { it == today } ?:
                preferredDates.sorted().lastOrNull() ?:
                today
            return NewEvent("", date.atTime(12, 0, 0), true)
        }

        fun make(otherEvents: List<Event>): NewEvent =
            make(LocalDate.now(), otherEvents.map { it.time.toLocalDate() })
    }
}

data class Event(
    @property:Field(type = InputType.hidden)
    val id: Int,
    @property:Field(order = 0, label = "Event name")
    val name: String,
    @property:Field(order = 1, label = "Time and date", type = InputType.dateTimeLocal)
    val time: LocalDateTime,
    @property:Field(order = 2, label = "Show in public schedule", type = InputType.checkBox)
    val visible: Boolean,
) : Validateable<Event> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name)
    )

    companion object {
        val fromRow: (Row) -> Event = { row -> Event(
            id = row.int("id"),
            name = row.string("name"),
            time = row.localDateTime("time"),
            visible = row.boolean("visible"),
        ) }
    }
}
