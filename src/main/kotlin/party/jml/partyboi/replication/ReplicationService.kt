@file:UseSerializers(
    OptionSerializer::class,
)

package party.jml.partyboi.replication

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.raise.either
import arrow.core.serialization.OptionSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.User
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.NotReady
import party.jml.partyboi.data.PropertyRow
import party.jml.partyboi.db.many
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.screen.ScreenRow
import party.jml.partyboi.screen.SlideSetRow
import party.jml.partyboi.triggers.TriggerRow
import party.jml.partyboi.voting.VoteRow

class ReplicationService(val app: AppServices) {
    private var schemaVersion: String? = null

    fun setSchemaVersion(version: String?) {
        schemaVersion = version
    }

    fun export(): Either<AppError, DataExport> {
        val version = schemaVersion
        return if (version == null) {
            NotReady().left()
        } else {
            either {
                DataExport(
                    schemaVersion = version,
                    users = app.users.getUsers().bind(),
                    compos = app.compos.getAllCompos().bind(),
                    entries = app.entries.getAllEntries().bind(),
                    events = app.events.getAll().bind(),
                    files = app.files.getAll().bind(),
                    properties = app.properties.getAll().bind(),
                    slides = app.screen.getAllSlides().bind(),
                    slideSets = app.screen.getSlideSets().bind(),
                    triggers = app.triggers.getAllTriggers().bind(),
                    votes = app.votes.getAllVotes().bind(),
                    voteKeys = getVoteKeys().bind(),
                )
            }
        }
    }

    // TODO: Move to an own repository
    private fun getVoteKeys(): Either<AppError, List<VoteKeyRow>> = app.db.use {
        it.many(queryOf("SELECT * FROM votekey").map(VoteKeyRow.fromRow))
    }
}

@Serializable
data class DataExport(
    val schemaVersion: String,
    val users: List<User>,
    val compos: List<Compo>,
    val entries: List<Entry>,
    val events: List<Event>,
    val files: List<FileDesc>,
    val properties: List<PropertyRow>,
    val slides: List<ScreenRow>,
    val slideSets: List<SlideSetRow>,
    val triggers: List<TriggerRow>,
    val votes: List<VoteRow>,
    val voteKeys: List<VoteKeyRow>,
)

@Serializable
data class VoteKeyRow(
    val key: String,
    val userId: Option<Int>,
) {
    companion object {
        val fromRow: (Row) -> VoteKeyRow = { row ->
            VoteKeyRow(
                key = row.string("key"),
                userId = Option.fromNullable(row.intOrNull("appuser_id")),
            )
        }
    }
}