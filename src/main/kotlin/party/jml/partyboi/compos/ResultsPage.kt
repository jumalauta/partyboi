package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.entries.Preview
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.IconSet
import party.jml.partyboi.templates.components.previewThumbnail
import party.jml.partyboi.templates.components.toggleButton
import party.jml.partyboi.voting.CompoResult
import java.util.*

data class ResultsPageData(
    val results: List<CompoResult>,
    val previews: Map<UUID, Preview>,
    val files: Map<UUID, FileDesc>,
    val publicCompoIds: Set<UUID>,
)

object ResultsPage {
    fun render(user: User?, data: ResultsPageData) = Page("Results") {
        div(classes = "results") {
            h1 { +"Results" }

            if (data.results.isEmpty()) {
                article {
                    +"No results available yet."
                }
            }

            val groupedResults = CompoResult.groupResults(data.results)

            if (groupedResults.isNotEmpty()) {
                nav(classes = "compo-nav") {
                    ul {
                        groupedResults.keys.forEach { compo ->
                            li { a(href = "#${compo.id}") { +compo.name } }
                        }
                        li { a(href = "/results.txt", classes = "results-txt") { +"results.txt" } }
                    }
                }
            }

            groupedResults.forEach { (compo, results) ->
                val isManual = results.any { it.results.any { r -> r.isManual } }
                val hasTitles = !isManual || results.any { it.results.any { r -> r.title.isNotBlank() } }
                val isPublic = compo.id in data.publicCompoIds
                val isAdmin = user?.isAdmin == true

                article(classes = if (isAdmin && !isPublic) "unpublished" else null) {
                    id = compo.id.toString()
                    header {
                        h3 { +compo.name.withCompoSuffix() }
                        if (isAdmin) {
                            toggleButton(isPublic, IconSet.resultsPublic, "/admin/compos/${compo.id}/publishResults")
                        }
                    }
                    results.forEach { (place, resultsForPlace) ->
                        resultsForPlace.forEach { result ->
                            resultCard(place, result, hasTitles, data)
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.resultCard(
        place: Int,
        result: CompoResult,
        hasTitles: Boolean,
        data: ResultsPageData,
    ) {
        val cardClasses = buildString {
            append("result-card")
            if (place <= 3) append(" place-$place")
        }
        div(classes = cardClasses) {
            div(classes = "place") { +"$place." }
            previewThumbnail(if (result.isManual) null else data.previews[result.entryId])
            div(classes = "result-body") {
                div(classes = "result-heading") {
                    span(classes = "result-title") {
                        if (hasTitles && result.title.isNotBlank()) {
                            +"${result.author} – ${result.title}"
                        } else {
                            +result.author
                        }
                    }
                    // Whitespace-only text nodes are ignored by the flex layout but keep
                    // extracted/copied text readable ("Demo #5 29 pts", not "Demo #529 pts").
                    +" "
                    if (result.isManual) {
                        result.scoreText?.let { span(classes = "points") { +it } }
                    } else {
                        span(classes = "points") { +"${result.points} pts" }
                    }
                }
                result.info?.let { p(classes = "entry-info") { +it } }
                if (!result.isManual) {
                    data.files[result.entryId]?.let { file ->
                        a(href = "/entries/download/${file.id}", classes = "download-link") {
                            attributes["download"] = ""
                            i(classes = "fa-solid fa-download") {}
                            +" Download "
                            code { +"${file.extension.uppercase()} · ${Filesize.humanFriendly(file.size)}" }
                        }
                    }
                }
            }
        }
    }
}
