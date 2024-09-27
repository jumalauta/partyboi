@file:Suppress("EscapedRaise")

package party.jml.partyboi.submit

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.ul
import party.jml.partyboi.auth.UserService
import party.jml.partyboi.data.megabytes
import party.jml.partyboi.database.*
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.respondEither

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
fun Application.configureSubmitRouting(db: DatabasePool) {
    val entries = EntryRepository(db)
    val compoRepository = CompoRepository(db)
    val users = UserService(db)

    fun submitEntriesPage(user: User): Either<AppError, Renderable> =
        either {
            val userEntries = entries.getUserEntries(user.id).bind()
            val compos = compoRepository.getOpenCompos().bind()

            Page("Submit entries") {
                h1 { +"Submit entries" }
                h2 { +"Hello, ${user.name}!" }
                ul {
                    userEntries.map { entry ->
                        li { +entry.title }
                    }
                }
                submitForm("/submit", compos)
            }
        }


    routing {
        get("/submit") {
            call.respondEither {
                users.currentUser(this).flatMap { submitEntriesPage(it) }
            }
        }

        post("/submit") {
            val userResult = users.currentUser(this)
            runBlocking {
                val params = call.receiveMultipart()
                val formResult = Form.fromParameters<NewEntry>(params)

                call.respondEither/*({ err -> user.flatMap { submitEntriesPage(it, err) } })*/ {
                    either {
                        val userId = userResult.bind().id
                        val form = formResult.bind()
                        val newEntry = form.validated().bind().copy(userId = userId)
                        runBlocking { newEntry.file.writeTo("/Users/ilkkahanninen/dev/temp/compos").bind() }
                        entries.add(newEntry).bind()
                        RedirectPage("/submit")
                    }
                }
            }
        }
    }
}
