package party.jml.partyboi.compos

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.optionalUserSession
import party.jml.partyboi.auth.publicRouting
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureComposRouting(app: AppServices) {
    publicRouting {
        get("/compos") {
            call.respondEither {
                val generalRules = app.compos.generalRules.get().bind()
                val compos = app.compos.getAllCompos().bind()
                ComposPage.render(generalRules, compos)
            }
        }

        get("/results") {
            call.respondEither {
                val user = call.optionalUserSession(app)
                val results = app.votes.getResultsForUser(user).bind()
                val entryIds = results.filterNot { it.isManual }.map { it.entryId }.toSet()
                val previews = app.previews.getEntryPreviewsById(entryIds.toList()).associateBy { it.entryId }
                val files = app.files.getLatestEntryFiles(includeProcessedFiles = false).bind()
                    .filter { it.entryId in entryIds }
                    .associate { it.entryId to it.file }
                val publicCompoIds = app.compos.getAllCompos().bind()
                    .filter { it.publicResults }
                    .map { it.id }
                    .toSet()
                ResultsPage.render(user, ResultsPageData(results, previews, files, publicCompoIds))
            }
        }

        get("/results.txt") {
            either {
                app.votes.getResultsFileContent(includeInfo = false, onlyPublic = true).bind()
            }.fold(
                { call.respondPage(it) },
                { call.respondText(it) }
            )
        }
    }
}
