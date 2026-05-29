package party.jml.partyboi.compos.admin

import arrow.core.raise.either
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.withCompoSuffix
import party.jml.partyboi.data.UUIDSerializer
import party.jml.partyboi.screen.slides.Slide
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.voting.CompoResult
import java.util.*


class ResultsRunService(app: AppServices) : Service(app) {
    val resultsSteps = property<ResultsSteps?>("resultsSteps", null)

    suspend fun initResultsSteps(compo: Compo): AppResult<ResultsSteps> = either {
        val currentState = resultsSteps.get().bind()

        if (currentState == null || currentState.hasEnded() || currentState.compoId != compo.id) {
            val results = app.votes.getResults().bind().filter { it.compoId == compo.id }
            val grouped = CompoResult.groupResults(results).values.firstOrNull().orEmpty()

            val chunks = grouped
                .fold(emptyList<List<CompoResult.Companion.GroupedCompoResult>>()) { acc, placeAndResult ->
                    when (placeAndResult.place) {
                        in 1..4 -> acc + listOf(listOf(placeAndResult))
                        else -> {
                            val last = acc.lastOrNull()
                            if (last == null) {
                                listOf(listOf(placeAndResult))
                            } else {
                                val entriesCollected = last.sumOf { it.results.size }
                                if (entriesCollected + placeAndResult.results.size > 5) {
                                    acc + listOf(listOf(placeAndResult))
                                } else {
                                    acc.take(acc.size - 1) + listOf(last + placeAndResult)
                                }
                            }
                        }
                    }
                }
                .reversed()
                .map { chunk -> PlacesChunk.fromGrouped(chunk) }

            val winnerChunk = chunks.lastOrNull()
            val placesChunks = if (winnerChunk == null) chunks else chunks.dropLast(1)

            val steps = listOf<ResultsStep>(ResultsStep.Intro(compo.name, compo.id)) +
                    placesChunks.map { ResultsStep.Places(compo.id, it) } +
                    listOf(ResultsStep.End(compo.name, compo.id, winnerChunk))

            val result = ResultsSteps(compo.id, null, steps)
            resultsSteps.set(result).bind()

            result
        } else {
            currentState
        }
    }

    suspend fun nextResultsStep() = either {
        resultsSteps.get().bind()?.let { steps ->
            resultsSteps.set(steps.next(app))
        }
    }

    suspend fun prevResultsStep() = either {
        resultsSteps.get().bind()?.let { steps ->
            resultsSteps.set(steps.prev(app))
        }
    }
}

@Serializable
data class ResultsSteps(
    @Serializable(with = UUIDSerializer::class)
    val compoId: UUID,
    val current: Int?,
    val steps: List<ResultsStep>,
) {
    suspend fun next(app: AppServices): ResultsSteps = activateStep(current?.let { it + 1 } ?: 0, app)
    suspend fun prev(app: AppServices): ResultsSteps {
        current?.let { steps[it].undo(app) }
        return activateStep(current?.let { it - 1 } ?: 0, app)
    }

    suspend fun activateStep(index: Int, app: AppServices): ResultsSteps =
        if (index >= 0 && index < steps.size) {
            val step = steps[index]
            step.activate(app)
            app.screen.showInMemorySlide(step.getSlide())
            copy(current = index)
        } else {
            this
        }

    fun hasEnded(): Boolean = current == steps.lastIndex
}

@Serializable
data class PlacesChunk(
    val title: String,
    val rows: List<String>,
) {
    companion object {
        fun fromGrouped(chunk: List<CompoResult.Companion.GroupedCompoResult>): PlacesChunk {
            val places = chunk.map { it.place }
            val minPlace = places.min()
            val maxPlace = places.max()
            val title = when {
                minPlace == 1 -> "Winner"
                minPlace == maxPlace -> "Place $minPlace"
                else -> "Places $minPlace-$maxPlace"
            }
            val rows = chunk.flatMap { pr ->
                pr.results.map {
                    val score = if (it.isManual) it.scoreText ?: "" else "${it.points} pts."
                    val suffix = if (score.isNotBlank()) " ($score)" else ""
                    val name = if (it.title.isBlank()) it.author else "${it.author} – ${it.title}"
                    "* ${pr.place}. $name$suffix"
                }
            }
            return PlacesChunk(title, rows)
        }
    }
}

@Serializable
sealed interface ResultsStep {
    fun title(): String
    fun icon(): String
    fun notes(): String? = null
    fun getSlide(): Slide<*>
    suspend fun activate(app: AppServices) {}
    suspend fun undo(app: AppServices) {}

    @Serializable
    data class Intro(
        val compoName: String,
        @Serializable(with = UUIDSerializer::class)
        val compoId: UUID,
    ) : ResultsStep {
        override fun title() = "Next: ${compoName.withCompoSuffix()} results"
        override fun icon() = "square-poll-horizontal"
        override fun getSlide() = TextSlide(
            "Next: ${compoName.withCompoSuffix()} results",
            "",
            TextSlide.CompoInfoVariant,
        )
    }

    @Serializable
    data class Places(
        @Serializable(with = UUIDSerializer::class)
        val compoId: UUID,
        val chunk: PlacesChunk,
    ) : ResultsStep {
        override fun title() = chunk.title
        override fun icon() = "trophy"
        override fun notes(): String = chunk.rows.joinToString(separator = "\n")
        override fun getSlide() = TextSlide(
            chunk.title,
            chunk.rows.joinToString(separator = "\n"),
            "results",
        )
    }

    @Serializable
    data class End(
        val compoName: String,
        @Serializable(with = UUIDSerializer::class)
        val compoId: UUID,
        val winnerChunk: PlacesChunk? = null,
    ) : ResultsStep {
        override fun title() = if (winnerChunk != null) "Winner of ${compoName.withCompoSuffix()}"
        else "${compoName.withCompoSuffix()} results published"

        override fun icon() = "trophy"

        override fun getSlide() = if (winnerChunk != null) TextSlide(
            winnerChunk.title,
            winnerChunk.rows.joinToString(separator = "\n"),
            "results",
        ) else TextSlide(
            "${compoName.withCompoSuffix()} results",
            "",
            TextSlide.CompoInfoVariant,
        )

        override suspend fun activate(app: AppServices) {
            app.compos.publishResults(compoId, true)
            app.compos.setVisible(compoId, true)
        }

        override suspend fun undo(app: AppServices) {
            app.compos.publishResults(compoId, false)
        }

        override fun notes(): String = listOfNotNull(
            winnerChunk?.rows?.joinToString(separator = "\n"),
            "Publishes the results and makes the compo visible.",
        ).joinToString(separator = "\n")
    }
}
