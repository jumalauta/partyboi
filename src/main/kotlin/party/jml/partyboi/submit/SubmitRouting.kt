@file:Suppress("EscapedRaise")

package party.jml.partyboi.submit

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import party.jml.partyboi.database.*
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.form.Form
import party.jml.partyboi.plugins.userSession
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.Renderable
import party.jml.partyboi.templates.respondEither

fun Application.configureSubmitRouting(db: DatabasePool) {
    val entries = EntryRepository(db)
    val compoRepository = CompoRepository(db)
    val users = UserRepository(db)

    fun submitEntriesPage(user: User, formData: Form<NewEntry> = Form(NewEntry::class, NewEntry.Empty, initial = true)): Either<AppError, Renderable> =
        either {
            val userEntries = entries.getUserEntries(user.id).bind()
            val compos = compoRepository.getAllCompos().bind()

            Page("Submit entries") {
                submitForm("/submit", compos.filter { it.allowSubmit }, formData)

                article {
                    header { +"My entries" }
                    if (userEntries.isEmpty()) {
                        p { +"Nothing yet, please upload something soon!" }
                    } else {
                        table {
                            thead {
                                tr {
                                    th { +"Title" }
                                    th { +"Author" }
                                    th { +"Compo" }
                                    th { +"File" }
                                    th {}
                                }
                            }
                            tbody {
                                userEntries.map { entry ->
                                    val compo = compos.find { it.id == entry.compoId }
                                    tr {
                                        td { +entry.title }
                                        td { +entry.author }
                                        td { +(compo?.name ?: "Invalid compo") }
                                        td { +entry.filename }
                                        td {
                                            if (compo?.allowSubmit == true) {
                                                a {
                                                    href="/submit/delete/${entry.id}"
                                                    role = "button"
                                                    +"Delete"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


    routing {
        authenticate("user") {
            get("/submit") {
                call.respondEither {
                    call.userSession().flatMap { submitEntriesPage(it) }
                }
            }

            post("/submit") {
                val maybeUser = call.userSession()

                runBlocking {
                    val params = call.receiveMultipart()
                    val submitRequest = Form.fromParameters<NewEntry>(params)

                    call.respondEither({ _ ->
                        either {
                            submitEntriesPage(maybeUser.bind(), submitRequest.bind())
                        }.flatten()
                    }) {
                        either {
                            val userId = maybeUser.bind().id
                            val form = submitRequest.bind()
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
}
