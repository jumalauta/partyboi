package party.jml.partyboi.voting

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.User
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.entries.Entry

class VoteService(val app: AppServices) {
    private val repository = VoteRepository(app.db)
    private val live = MutableStateFlow(LiveVoteState.Empty)

    fun castVote(userId: Int, entryId: Int, points: Int): Either<AppError, Unit> =
        either {
            val validPoints = validatePoints(points).bind()
            val entry = app.entries.get(entryId).bind()
            if (isVotingOpen(entry).bind()) {
                repository.castVote(userId, entryId, validPoints).bind()
            } else {
                InvalidInput("This entry cannot be voted").left()
            }
        }

    suspend fun startLiveVoting(compoId: Int) = live.emit(LiveVoteState(true, compoId, emptyList()))
    suspend fun addEntryToLiveVoting(entry: Entry) = live.emit(live.value.with(entry))
    suspend fun closeLiveVoting() = live.emit(LiveVoteState.Empty)

    fun waitForLiveVoteUpdate(): Flow<LiveVoteState> {
        val current = live.value
        return live.filter { it != current }.take(1)
    }
    
    fun getResults(user: Option<User>): Either<AppError, List<CompoResult>> =
        repository.getResults(onlyPublic = user.fold({ true }, { !it.isAdmin }))

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
    val compoId: Int,
    val entries: List<Entry>,
) {
    fun openFor(entry: Entry): Boolean =
        open && compoId == entry.compoId && entries.find { it.id == entry.id } != null

    fun with(entry: Entry) = copy(entries = entries + entry)

    companion object {
        val Empty = LiveVoteState(false, -1, emptyList())
    }
}