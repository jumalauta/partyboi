package party.jml.partyboi.voting

import arrow.core.*
import arrow.core.raise.either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.auth.User
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.ResultsFileGenerator
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.data.Unauthorized
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.VotableEntry
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.signals.SignalType
import party.jml.partyboi.system.AppResult
import java.util.*

class VoteService(app: AppServices) : Service(app) {
    private val repository = VoteRepository(app)
    private val live = MutableStateFlow(LiveVoteState.Empty)

    suspend fun start() {
        app.signals.flow.collect {
            if (it.type == SignalType.propertyUpdated && it.target == "SettingsService.voteKeyEmailList") {
                app.settings.automaticVoteKeys.get().onRight { autoKeys ->
                    if (autoKeys == AutomaticVoteKeys.PER_EMAIL) {
                        app.voteKeys.grantVotingRightsByEmail()
                    }
                }
            }
        }
    }

    suspend fun castVote(user: User, entryId: UUID, points: Int): AppResult<Unit> =
        if (user.votingEnabled) {
            either {
                val validPoints = validatePoints(points).bind()
                val entry = app.entries.getById(entryId).bind()
                if (isVotingOpen(entry).bind()) {
                    repository.castVote(user.id, entryId, validPoints).bind()
                } else {
                    raise(InvalidInput("This entry cannot be voted"))
                }
            }
        } else {
            Unauthorized().left()
        }

    suspend fun startLiveVoting(compoId: UUID) {
        either {
            val compo = app.compos.getById(compoId).bind()
            live.emit(LiveVoteState(true, compo, emptyList()))
            app.signals.emit(Signal.liveVotingOpened(compoId))
        }
    }

    suspend fun addEntryToLiveVoting(entry: Entry) {
        live.updateAndGet { it.with(entry) }
        app.signals.emit(Signal.liveVotingEntry(entry.id))
    }

    suspend fun removeEntryFromLiveVoting(entry: Entry) {
        val newState = live.updateAndGet { it.without(entry) }
        newState.entries.lastOrNull()?.let { app.signals.emit(Signal.liveVotingEntry(it.id)) }
    }

    suspend fun closeLiveVoting() {
        live.emit(LiveVoteState.Empty)
        app.signals.emit(Signal.liveVotingClosed())
    }

    fun getLiveVoteState(): Option<LiveVoteState> = if (live.value.open) live.value.some() else none()

    suspend fun getVotableEntries(userId: UUID): AppResult<List<VotableEntry>> =
        either {
            val normalVoting = app.entries.getVotableEntries(userId).bind()
            val liveVoting = if (live.value.open) {
                val userVotes = repository.getUserVotes(userId).bind()
                live.value.entries
                    .sortedWith(compareBy({ it.runOrder }, { it.timestamp }))
                    .reversed()
                    .map { entry ->
                        VotableEntry.apply(
                            entry,
                            "Live: ${live.value.compo.name}",
                            userVotes.find { it.entryId == entry.id }?.points
                        )
                    }
            } else emptyList()

            liveVoting + normalVoting
        }

    suspend fun getAllVotes(): AppResult<List<VoteRow>> = repository.getAllVotes()

    suspend fun getResults(): AppResult<List<CompoResult>> = either {
        val voteResults = repository.getResults(onlyPublic = false).bind()
        val manualResults = app.manualResults.getResults(onlyPublic = false).bind()
        voteResults + manualResults
    }

    suspend fun getResultsForUser(user: Option<User>): AppResult<List<CompoResult>> =
        either {
            val onlyPublic = user.fold({ true }, { !it.isAdmin })
            val downloads = app.files.getEntryIdsWithFiles(includeProcessedFiles = false).bind()
            val voteResults = repository
                .getResults(onlyPublic = onlyPublic)
                .bind()
                .map { entry ->
                    val download = downloads.find { it.entryId == entry.entryId }
                    if (download != null) {
                        entry.copy(downloadLink = "/entries/download/${download.fileId}")
                    } else {
                        entry
                    }
                }
            val manualResults = app.manualResults.getResults(onlyPublic = onlyPublic).bind()
            voteResults + manualResults
        }

    suspend fun getResultsFileContent(includeInfo: Boolean): AppResult<String> = either {
        val header = app.settings.resultsFileHeader.get().bind()
        val results = getResults().bind()
        ResultsFileGenerator.generate(header, results, includeInfo)
    }

    suspend fun deleteAll() = repository.deleteAll()

    private suspend fun isVotingOpen(entry: Entry): AppResult<Boolean> =
        if (!entry.qualified) {
            false.right()
        } else if (live.value.openFor(entry)) {
            true.right()
        } else {
            app.compos.isVotingOpen(entry.compoId)
        }

    private fun validatePoints(points: Int): AppResult<Int> =
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

    fun with(entry: Entry) = without(entry).let { it.copy(entries = it.entries + entry) }

    fun without(entry: Entry) = copy(entries = entries.filter { it.id != entry.id })

    companion object {
        val Empty = LiveVoteState(false, Compo.Empty, emptyList())
    }
}