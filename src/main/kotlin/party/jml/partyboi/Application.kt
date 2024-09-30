package party.jml.partyboi

import io.ktor.server.application.*
import party.jml.partyboi.auth.configureLoginRouting
import party.jml.partyboi.database.getDatabasePool
import party.jml.partyboi.plugins.*
import party.jml.partyboi.entries.configureSubmitRouting
import party.jml.partyboi.templates.configureStyles

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val db = getDatabasePool(embedded = false)

    configureSerialization()
    configureHTTP()
    configureAuthentication(db)
    configureStyles()
    configureStaticContent()

    configureDefaultRouting()
    configureLoginRouting(db)
    configureSubmitRouting(db)
}
