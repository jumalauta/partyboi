package party.jml.partyboi.voting

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.entries.Entry

class VoteService(val app: AppServices) {
    private val repository = VoteRepository(app.db)

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

    private fun isVotingOpen(entry: Entry): Either<AppError, Boolean> =
        app.compos.isVotingOpen(entry.compoId)

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