@file:UseSerializers(
    LocalDateTimeIso8601Serializer::class,
)

package party.jml.partyboi.schedule

import arrow.core.raise.either
import kotlinx.datetime.*
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.db.*
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Label
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.TimeService
import party.jml.partyboi.triggers.Action
import party.jml.partyboi.triggers.TriggerRow
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable

class EventRepository(private val app: AppServices) : Logging() {
    private val db = app.db

    suspend fun get(eventId: Int): AppResult<Event> = db.use {
        it.one(queryOf("SELECT * FROM event WHERE id = ?", eventId).map(Event.fromRow))
    }

    suspend fun getBetween(since: LocalDateTime, until: LocalDateTime): AppResult<List<Event>> = db.use {
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
                visible = ?
            WHERE id = ?
            RETURNING *""",
                event.name,
                event.startTime,
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

data class NewEvent(
    @Label("Event name")
    @NotEmpty
    val name: String,
    @Label("Start time and date")
    val startTime: LocalDateTime,
    @Label("End time and date")
    val endTime: LocalDateTime? = null,
    @Label("Show in public schedule")
    val visible: Boolean,
) : Validateable<NewEvent> {
    companion object {
        fun make(today: LocalDate, preferredDates: List<LocalDate>): NewEvent {
            val date = preferredDates.find { it == today } ?: preferredDates.maxOrNull() ?: today
            return NewEvent(
                name = "",
                startTime = date.atTime(12, 0, 0),
                endTime = null,
                visible = true
            )
        }

        suspend fun make(otherEvents: List<Event>, timeService: TimeService): NewEvent {
            val timeZone = timeService.timeZone.get().getOrNull()!!
            return make(timeService.today(), otherEvents.map { it.startTime.toLocalDateTime(timeZone).date })
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
    val startTime: Instant,
    @Label("End time and date")
    val endTime: Instant?,
    @Label("Show in public schedule")
    val visible: Boolean,
) : Validateable<Event> {
    fun signal(): Signal = Signal.eventStarted(id)

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
