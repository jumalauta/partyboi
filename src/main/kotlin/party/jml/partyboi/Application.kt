package party.jml.partyboi

import io.ktor.server.application.*
import party.jml.partyboi.auth.configureAuthentication
import party.jml.partyboi.auth.configureLoginRouting
import party.jml.partyboi.compos.configureComposRouting
import party.jml.partyboi.database.getDatabasePool
import party.jml.partyboi.plugins.*
import party.jml.partyboi.entries.configureEntriesRouting
import party.jml.partyboi.templates.configureStyles

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val db = getDatabasePool(embedded = false)
    val app = AppServices(db)

    configureSerialization()
    configureHTTP()
    configureAuthentication(app)
    configureStyles()
    configureStaticContent()

    configureDefaultRouting()
    configureLoginRouting(app)
    configureEntriesRouting(app)
    configureComposRouting(app)
}
