package party.jml.partyboi.admin.compos

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Application.configureAdminComposRouting(app: AppServices) {

    fun renderAdminComposPage(
        newCompoForm: Form<NewCompo>? = null,
        generalRulesForm: Form<GeneralRules>? = null,
    ) = either {
        AdminComposPage.render(
            newCompoForm = newCompoForm ?: Form(NewCompo::class, NewCompo.Empty, initial = true),
            generalRulesForm = generalRulesForm ?: Form(
                GeneralRules::class,
                app.compos.getGeneralRules().bind(),
                initial = true
            ),
            compos = app.compos.getAllCompos().bind(),
            entries = app.entries.getAllEntriesByCompo().bind(),
        )
    }

    fun renderAdminEditCompoPage(
        compoId: Either<AppError, Int>,
        compoForm: Form<Compo>? = null,
    ) = either {
        val id = compoId.bind()
        AdminEditCompoPage.render(
            compoForm = compoForm ?: Form(Compo::class, app.compos.getById(id).bind(), initial = true),
            entries = app.entries.getEntriesForCompo(id).bind(),
            compos = app.compos.getAllCompos().bind(),
        )
    }

    adminRouting {
        val redirectionToCompos = Redirection("/admin/compos")

        get("/admin/compos") {
            call.respondEither({ renderAdminComposPage() })
        }

        post("/admin/compos") {
            call.processForm<NewCompo>(
                { app.compos.add(it).map { redirectionToCompos } },
                { renderAdminComposPage(newCompoForm = it) }
            )
        }

        post("/admin/compos/generalRules") {
            call.processForm<GeneralRules>(
                { app.compos.setGeneralRules(it).map { redirectionToCompos } },
                { renderAdminComposPage(generalRulesForm = it) }
            )
        }

        get("/admin/compos/{id}") {
            call.respondEither({
                renderAdminEditCompoPage(call.parameterInt("id"))
            })
        }

        post("/admin/compos/{id}") {
            call.processForm<Compo>(
                { app.compos.update(it).map { redirectionToCompos } },
                { renderAdminEditCompoPage(call.parameterInt("id"), it) }
            )
        }

        get("/admin/compos/{id}/download") {
            either {
                val compoId = call.parameterInt("id").bind()
                val compo = app.compos.getById(compoId).bind()
                val entries = app.compoRun.prepareFiles(compoId).bind()
                val zipFile = app.compoRun.compressDirectory(entries).bind()

                val compoName = compo.name.toFilenameToken(true)
                val timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toFilenameToken(true)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "$compoName-compo-$timestamp.zip"
                    ).toString()
                )
                call.respondFile(zipFile.toFile())
            }
        }

        get("/admin/compos/{id}/generate-slides") {
            either {
                val compoId = call.parameterInt("id").bind()
                val slideEditUrl = app.screen.generateSlidesForCompo(compoId).bind()
                call.respondRedirect(slideEditUrl)
            }
        }

        get("/admin/compos/{id}/generate-result-slides") {
            either {
                val compoId = call.parameterInt("id").bind()
                val slideEditUrl = app.screen.generateResultSlidesForCompo(compoId).bind()
                call.respondRedirect(slideEditUrl)
            }
        }

        get("/admin/compos/results.txt") {
            either {
                call.respondText(app.votes.getResultsFileContent().bind())
            }.onLeft {
                call.respondPage(it)
            }
        }

    }

    adminApiRouting {
        put("/admin/compos/{id}/setVisible/{state}") {
            call.switchApi { id, state -> app.compos.setVisible(id, state) }
        }

        put("/admin/compos/{id}/setSubmit/{state}") {
            call.switchApi { id, state -> app.compos.allowSubmit(id, state) }
        }

        put("/admin/compos/{id}/setVoting/{state}") {
            call.switchApi { id, state -> app.compos.allowVoting(id, state) }
        }

        put("/admin/compos/{id}/publishResults/{state}") {
            call.switchApi { id, state -> app.compos.publishResults(id, state) }
        }

        put("/admin/compos/entries/{id}/setQualified/{state}") {
            call.switchApi { id, state -> app.entries.setQualified(id, state) }
        }

        post("/admin/compos/{compoId}/runOrder") {
            either {
                val runOrder = call.receive<List<String>>()
                val compoId = call.parameterInt("compoId").bind()
                runOrder
                    .mapIndexed { index, entryId -> app.entries.setRunOrder(entryId.toInt(), index) }
                    .bindAll()
                runBlocking { app.signals.emit(Signal.compoContentUpdated(compoId)) }
                call.respondText("OK")
            }
        }
    }
}