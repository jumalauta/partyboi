package party.jml.partyboi.voting

import kotlinx.serialization.Serializable
import party.jml.partyboi.validation.Validateable
import java.util.*
import kotlin.math.roundToInt

@Serializable
data class VotingSettings(
    val scale: PointScale,
    val emptyVotes: EmptyVoteHandling,
    val scoring: ScoringMethod,
) : Validateable<VotingSettings> {
    fun countVotes(votes: List<Voting>) = scoring.countVotes(votes, emptyVotes, scale)

    fun override(
        scale: PointScale?,
        emptyVotes: EmptyVoteHandling?,
        scoring: ScoringMethod?
    ) = copy(
        scale = scale ?: this.scale,
        emptyVotes = emptyVotes ?: this.emptyVotes,
        scoring = scoring ?: this.scoring,
    )

    companion object {
        val Default = from(null, null, null)

        fun from(
            scale: PointScale?,
            emptyVotes: EmptyVoteHandling?,
            scoring: ScoringMethod?,
        ) = VotingSettings(
            scale = scale ?: PointScale(1, 5),
            emptyVotes = emptyVotes ?: EmptyVoteHandling.Ignore,
            scoring = scoring ?: ScoringMethod.Additive,
        )
    }
}

@Serializable
data class PointScale(
    val min: Int,
    val max: Int,
) {
    val medium: Double by lazy { listOf(min, max).average() }
    val range: IntRange by lazy { (min..max) }
}

data class Voting(
    val entryId: UUID,
    val votes: List<Int?>,
)

data class Result(
    val entryId: UUID,
    val score: Int,
)

enum class EmptyVoteHandling(
    val label: String,
    val handle: (List<Int?>, PointScale) -> List<Double>
) {
    Ignore(
        "Empty votes are ignored",
        handle = { points, scale -> points.filterNotNull().map { it.toDouble() } }
    ),
    MinimumPoints(
        label = "Empty votes give minimum points on scale",
        handle = { points, scale -> points.map { (it ?: scale.min).toDouble() } }),
    MediumPoints(
        label = "Empty votes give average points on scale",
        handle = { points, scale -> points.map { (it ?: scale.medium).toDouble() } }),
    AveragePoints(
        label = "Empty votes give average of given points",
        handle = { points, scale ->
            val average = points.filterNotNull().average().let { avg ->
                if (avg.isFinite()) avg else scale.medium
            }
            points.map { pt -> pt?.toDouble() ?: average }
        })
}

enum class ScoringMethod(
    val label: String,
    val countVotes: (List<Voting>, EmptyVoteHandling, PointScale) -> List<Result>
) {
    Additive("Points are added together", { votes, mapEmptyVotes, scale ->
        votes.map {
            Result(
                entryId = it.entryId,
                score = mapEmptyVotes.handle(it.votes, scale).sum().roundToInt(),
            )
        }
    }),

    TrimmedAdditive(
        "Points are added together; Two highest and lowest votes are removed, unless there are three or less votes left",
        { votes, mapEmptyVotes, scale ->
            votes.map { voting ->
                val v = mapEmptyVotes.handle(voting.votes, scale)
                    .sorted()
                    .toMutableList()

                if (v.size > 3) v.removeLast()
                if (v.size > 3) v.removeFirst()
                if (v.size > 3) v.removeLast()
                if (v.size > 3) v.removeFirst()

                Result(
                    entryId = voting.entryId,
                    score = v.sum().roundToInt(),
                )
            }
        })
}
