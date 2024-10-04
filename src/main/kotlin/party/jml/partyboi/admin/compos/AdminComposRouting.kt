package party.jml.partyboi.admin.compos

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.NewCompo
import party.jml.partyboi.errors.catchError
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither

fun Application.configureAdminComposRouting(app: AppServices) {
    routing {
        authenticate("admin") {
            get("/admin/compos") {
                call.respondEither({ either {
                    val compos = app.compos.getAllCompos().bind()
                    val entries = app.entries.getAllEntriesByCompo().bind()
                    val newCompo = Form(NewCompo::class, NewCompo.Empty, initial = true)
                    AdminComposPage.render(newCompo, compos, entries)
                }})
            }

            post("/admin/compos") {
                val newCompo = Form.fromParameters<NewCompo>(call.receiveMultipart())
                call.respondEither({ either {
                    app.compos.add(newCompo.bind().validated().bind()).bind()
                    RedirectPage("/admin/compos")
                }}, { error -> either {
                    val compos = app.compos.getAllCompos().bind()
                    val entries = app.entries.getAllEntriesByCompo().bind()
                    AdminComposPage.render(newCompo.bind().with(error), compos, entries)
                }})
            }

            get("/admin/compos/{id}") {
                call.respondEither({ either {
                    val id = catchError { call.parameters["id"]?.toInt() ?: -1 }.bind()
                    val compo = app.compos.getById(id).bind()
                    val form = Form(Compo::class, compo, initial = true)
                    val entries = app.entries.getEntriesForCompo(id).bind()
                    AdminEditCompoPage.render(form, entries)
                }})
            }

            post("/admin/compos/{id}") {
                val compo = Form.fromParameters<Compo>(call.receiveMultipart())
                call.respondEither({ either {
                    app.compos.update(compo.bind().validated().bind()).bind()
                    RedirectPage("/admin/compos")
                } }, { error -> either {
                    val entries = app.entries.getEntriesForCompo(compo.bind().data.id).bind()
                    AdminEditCompoPage.render(compo.bind().with(error), entries)
                } })
            }
        }

        // API routes (we don't want to redirect user to login page)
        authenticate("admin", optional = true) {
            put("/admin/compos/{id}/setSubmit/{state}") {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid id")
                val state = call.parameters["state"]?.toBoolean() ?: throw IllegalArgumentException("Invalid state")
                app.compos.allowSubmit(id, state)
                call.respondText("OK")
            }

            put("/admin/compos/{id}/setVoting/{state}") {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid id")
                val state = call.parameters["state"]?.toBoolean() ?: throw IllegalArgumentException("Invalid state")
                app.compos.allowVoting(id, state)
                call.respondText("OK")
            }

            put("/admin/compos/entries/{id}/setQualified/{state}") {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid id")
                val state = call.parameters["state"]?.toBoolean() ?: throw IllegalArgumentException("Invalid state")
                app.entries.setQualified(id, state)
                call.respondText("OK")
            }

            put("/admin/compos/{compoId}/runOrder") {
                call.receive<List<String>>()
                    .mapIndexed { index, entryId -> app.entries.setRunOrder(entryId.toInt(), index) }
                call.respondText("OK")
            }
        }
    }
}