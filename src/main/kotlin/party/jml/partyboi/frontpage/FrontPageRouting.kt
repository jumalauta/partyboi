package party.jml.partyboi.frontpage

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.optionalUserSession
import party.jml.partyboi.auth.publicRouting
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.templates.respondEither

fun Application.configureFrontPageRouting(app: AppServices) {
    publicRouting {
        get("/") {
            val user = call.optionalUserSession(app)
            if (user?.isAdmin == true && app.settings.wizardCompleted.getOrNull() != true) {
                call.respondRedirect("/wizard")
                return@get
            }
            call.respondEither {
                val events = app.events.getAll().bind().filter { it.visible }
                val infoScreen = app.screen
                    .getSlideSet(SlideSetRow.DEFAULT).bind()
                    .filter { it.showOnInfoPage }
                val liveVote = app.votes.getLiveVoteState()
                FrontPage.render(events, infoScreen, liveVote)
            }
        }
    }
}