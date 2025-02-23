package party.jml.partyboi.voting

import arrow.core.*
import arrow.core.raise.either
import kotlinx.coroutines.flow.MutableStateFlow
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.User
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.ResultsFileGenerator
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.data.Unauthorized
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.VotableEntry
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.signals.Signal

class VoteService(val app: AppServices) {
    private val repository = VoteRepository(app.db)
    private val live = MutableStateFlow(LiveVoteState.Empty)

    fun castVote(user: User, entryId: Int, points: Int): Either<AppError, Unit> =
        if (user.votingEnabled) {
            either {
                val validPoints = validatePoints(points).bind()
                val entry = app.entries.get(entryId).bind()
                if (isVotingOpen(entry).bind()) {
                    repository.castVote(user.id, entryId, validPoints).bind()
                } else {
                    InvalidInput("This entry cannot be voted").left()
                }
            }
        } else {
            Unauthorized().left()
        }

    suspend fun startLiveVoting(compoId: Int) {
        either {
            val compo = app.compos.getById(compoId).bind()
            live.emit(LiveVoteState(true, compo, emptyList()))
            app.signals.emit(Signal.liveVotingOpened(compoId))
        }
    }

    suspend fun addEntryToLiveVoting(entry: Entry) {
        live.emit(live.value.with(entry))
        app.signals.emit(Signal.liveVotingEntry(entry.id))
    }

    suspend fun closeLiveVoting() {
        live.emit(LiveVoteState.Empty)
        app.signals.emit(Signal.liveVotingClosed())
    }

    fun getLiveVoteState(): Option<LiveVoteState> = if (live.value.open) live.value.some() else none()

    fun getVotableEntries(userId: Int): Either<AppError, List<VotableEntry>> =
        either {
            val normalVoting = app.entries.getVotableEntries(userId).bind()
            val liveVoting = if (live.value.open) {
                val userVotes = repository.getUserVotes(userId).bind()
                live.value.entries.map { entry ->
                    VotableEntry.apply(
                        entry,
                        "Live: ${live.value.compo.name}",
                        userVotes.find { it.entryId == entry.id }?.points
                    )
                }
            } else emptyList()

            liveVoting + normalVoting
        }

    fun getAllVotes(): Either<AppError, List<VoteRow>> = repository.getAllVotes()

    fun getResults(): Either<AppError, List<CompoResult>> =
        repository.getResults(onlyPublic = false)

    fun getResultsForUser(user: Option<User>): Either<AppError, List<CompoResult>> =
        repository.getResults(onlyPublic = user.fold({ true }, { !it.isAdmin }))

    fun getResultsFileContent(): Either<AppError, String> = either {
        val header = app.settings.resultsFileHeader.get().bind()
        val results = getResults().bind()
        ResultsFileGenerator.generate(header, results)
    }

    fun import(tx: TransactionalSession, data: DataExport) = repository.import(tx, data)

    fun deleteAll() = repository.deleteAll()

    private fun isVotingOpen(entry: Entry): Either<AppError, Boolean> =
        if (!entry.qualified) {
            false.right()
        } else if (live.value.openFor(entry)) {
            true.right()
        } else {
            app.compos.isVotingOpen(entry.compoId)
        }

    private fun validatePoints(points: Int): Either<AppError, Int> =
        if (points < MIN_POINTS || points > MAX_POINTS) {
            InvalidInput("Invalid points").left()
        } else {
            points.right()
        }

    companion object {
        const val MIN_POINTS = 1
        const val MAX_POINTS = 5
        val POINT_RANGE = MIN_POINTS..MAX_POINTS
    }
}

data class LiveVoteState(
    val open: Boolean,
    val compo: Compo,
    val entries: List<Entry>,
) {
    fun openFor(entry: Entry): Boolean =
        open && compo.id == entry.compoId && entries.find { it.id == entry.id } != null

    fun with(entry: Entry) = copy(entries = entries + entry)

    companion object {
        val Empty = LiveVoteState(false, Compo.Empty, emptyList())
    }
}