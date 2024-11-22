package party.jml.partyboi.schedule

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.*
import party.jml.partyboi.db.many
import party.jml.partyboi.db.one
import party.jml.partyboi.db.updateOne
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.triggers.TriggerRow
import party.jml.partyboi.triggers.Action
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

class EventRepository(private val app: AppServices) {
    private val db = app.db

    fun get(eventId: Int): Either<AppError, Event> = db.use {
        it.one(queryOf("SELECT * FROM event WHERE id = ?", eventId).map(Event.fromRow))
    }

    fun getBetween(since: LocalDateTime, until: LocalDateTime): Either<AppError, List<Event>> = db.use {
        it.many(queryOf("SELECT * FROM event WHERE time > ? AND time <= ?", since, until).map(Event.fromRow))
    }

    fun getAll(): Either<AppError, List<Event>> = db.use {
        it.many(queryOf("SELECT * FROM event ORDER BY time").map(Event.fromRow))
    }

    fun getPublic(): Either<AppError, List<Event>> = db.use {
        it.many(queryOf("SELECT * FROM event WHERE visible ORDER BY time").map(Event.fromRow))
    }

    fun add(event: NewEvent, tx: TransactionalSession? = null): Either<AppError, Event> = db.use(tx) {
        it.one(
            queryOf(
                """
            INSERT INTO event (name, time, visible) 
            VALUES (?, ?, ?) 
            RETURNING *""",
                event.name,
                Timestamp.valueOf(event.time),
                event.visible,
            ).map(Event.fromRow)
        )
    }

    fun add(event: NewEvent, actions: List<Action>): Either<AppError, Pair<Event, List<TriggerRow>>> =
        db.transaction { tx ->
            either {
                val createdEvent = add(event, tx).bind()
                val createdTriggers = actions
                    .map { app.triggers.add(createdEvent.signal(), it, tx) }
                    .bindAll()
                Pair(createdEvent, createdTriggers)
            }
        }

    fun update(event: Event, tx: TransactionalSession? = null): Either<AppError, Event> = db.use(tx) {
        app.triggers.reset(event.signal(), tx)
        it.one(
            queryOf(
                """
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
            ).map(Event.fromRow)
        )
    }

    fun delete(eventId: Int): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("DELETE FROM event WHERE id = ?", eventId))
    }
}

data class NewEvent(
    @property:Field(order = 0, label = "Event name")
    val name: String,
    @property:Field(order = 1, label = "Time and date")
    val time: LocalDateTime,
    @property:Field(order = 2, label = "Show in public schedule")
    val visible: Boolean,
) : Validateable<NewEvent> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
    )

    companion object {
        fun make(today: LocalDate, preferredDates: List<LocalDate>): NewEvent {
            val date = preferredDates.find { it == today } ?: preferredDates.sorted().lastOrNull() ?: today
            return NewEvent("", date.atTime(12, 0, 0), true)
        }

        fun make(otherEvents: List<Event>): NewEvent =
            make(LocalDate.now(), otherEvents.map { it.time.toLocalDate() })
    }
}

data class Event(
    @property:Field(presentation = FieldPresentation.hidden)
    val id: Int,
    @property:Field(order = 0, label = "Event name")
    val name: String,
    @property:Field(order = 1, label = "Time and date")
    val time: LocalDateTime,
    @property:Field(order = 2, label = "Show in public schedule")
    val visible: Boolean,
) : Validateable<Event> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name)
    )

    fun signal(): Signal = Signal.eventStarted(id)

    companion object {
        val fromRow: (Row) -> Event = { row ->
            Event(
                id = row.int("id"),
                name = row.string("name"),
                time = row.localDateTime("time"),
                visible = row.boolean("visible"),
            )
        }
    }
}
