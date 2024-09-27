package party.jml.partyboi

import io.ktor.server.application.*
import party.jml.partyboi.auth.configureLoginRouting
import party.jml.partyboi.database.getDatabasePool
import party.jml.partyboi.plugins.*
import party.jml.partyboi.submit.configureSubmitRouting
import party.jml.partyboi.templates.configureStyles

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val db = getDatabasePool(embedded = false)

    configureSerialization()
    configureHTTP()
    configureSecurity()
    configureRouting()
    configureStyles()

    configureLoginRouting(db)
    configureSubmitRouting(db)
}
