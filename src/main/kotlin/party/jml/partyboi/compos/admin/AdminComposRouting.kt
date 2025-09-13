package party.jml.partyboi.compos.admin

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.pre
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.data.*
import party.jml.partyboi.entries.respondFileShow
import party.jml.partyboi.entries.respondNamedFileDownload
import party.jml.partyboi.form.Form
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage
import java.nio.file.Path
import java.util.*
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

fun Application.configureAdminComposRouting(app: AppServices) {

    suspend fun renderAdminComposPage(
        newCompoForm: Form<NewCompo>? = null,
        generalRulesForm: Form<GeneralRules>? = null,
    ) = either {
        AdminComposPage.render(
            newCompoForm = newCompoForm ?: Form(NewCompo::class, NewCompo.Empty, initial = true),
            generalRulesForm = generalRulesForm ?: Form(
                GeneralRules::class,
                app.compos.generalRules.get().bind(),
                initial = true
            ),
            compos = app.compos.getAllCompos().bind(),
            entries = app.entries.getAllEntriesByCompo().bind(),
        )
    }

    suspend fun renderAdminEditCompoPage(
        compoId: AppResult<UUID>,
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
            call.respondEither { renderAdminComposPage().bind() }
        }

        post("/admin/compos") {
            call.processForm<NewCompo>(
                { app.compos.add(it).map { redirectionToCompos }.bind() },
                { renderAdminComposPage(newCompoForm = it).bind() }
            )
        }

        post("/admin/compos/generalRules") {
            call.processForm<GeneralRules>(
                {
                    app.compos.generalRules.set(it)
                    redirectionToCompos
                },
                { renderAdminComposPage(generalRulesForm = it).bind() }
            )
        }

        get("/admin/compos/{id}") {
            call.respondEither {
                renderAdminEditCompoPage(call.parameterUUID("id")).bind()
            }
        }

        post("/admin/compos/{id}") {
            call.processForm<Compo>(
                { app.compos.update(it).map { redirectionToCompos }.bind() },
                { renderAdminEditCompoPage(call.parameterUUID("id"), it).bind() }
            )
        }

        get("/admin/compos/{id}/download") {
            either {
                val compoId = call.parameterUUID("id").bind()
                val useFoldersForSingleFiles = call.request.queryParameters["win"] == "true"
                val compo = app.compos.getById(compoId).bind()
                val entries = app.compoRun.prepareFiles(compoId, useFoldersForSingleFiles).bind()
                val zipFile = app.compoRun.compressDirectory(entries.path).bind()

                val compoName = compo.name.toFilenameToken(true)
                val timestamp = app.time.isoLocalTime().toFilenameToken(true)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "$compoName-compo-$timestamp.zip"
                    ).toString()
                )
                call.respondFile(zipFile)
            }
        }

        get("/admin/compos/{id}/generate-slides") {
            either {
                val compoId = call.parameterUUID("id").bind()
                val slideEditUrl = app.screen.generateSlidesForCompo(compoId).bind()
                call.respondRedirect(slideEditUrl)
            }
        }

        get("/admin/compos/{id}/generate-result-slides") {
            either {
                val compoId = call.parameterUUID("id").bind()
                val slideEditUrl = app.screen.generateResultSlidesForCompo(compoId).bind()
                call.respondRedirect(slideEditUrl)
            }
        }

        get("/admin/compos/results.txt") {
            either {
                call.respondText(app.votes.getResultsFileContent(includeInfo = false).bind())
            }.onLeft {
                call.respondPage(it)
            }
        }

        get("/admin/compos/results-with-info.txt") {
            either {
                call.respondText(app.votes.getResultsFileContent(includeInfo = true).bind())
            }.onLeft {
                call.respondPage(it)
            }
        }

        get("/admin/compos/entries.zip") {
            either {
                call.respondNamedFileDownload(
                    app.compoRun.compressAllEntries().bind(),
                    "entries.zip"
                )
            }.onLeft {
                call.respondPage(it)
            }
        }

        get("/admin/host/{fileId}") {
            either {
                val fileId = call.parameterUUID("fileId").bind()
                val hostedEntry = app.compoRun.extractEntryFiles(fileId).bind()
                call.hostFile(hostedEntry)
            }.mapLeft { error ->
                call.respondPage(error)
            }
        }

        get("/admin/host/{fileId}/{path...}") {
            either {
                val fileId = call.parameterUUID("fileId").bind()
                val path = call.parameterPath("path") { Path.of(it) }.bind()
                val hostedEntry = app.compoRun.extractEntryFiles(fileId).bind()
                call.hostFile(hostedEntry, path)
            }.mapLeft { error ->
                call.respondPage(error)
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

        put("/admin/compos/entries/{id}/allowEdit/{state}") {
            call.switchApi { id, state -> app.entries.allowEdit(id, state) }
        }

        post("/admin/compos/{compoId}/runOrder") {
            either {
                val runOrder = call.receive<List<String>>()
                val compoId = call.parameterUUID("compoId").bind()
                runOrder
                    .mapIndexed { index, entryId -> app.entries.setRunOrder(UUID.fromString(entryId), index) }
                    .bindAll()
                app.signals.emit(Signal.compoContentUpdated(compoId, app.time))
                call.respondText("OK")
            }
        }
    }
}

suspend fun ApplicationCall.hostFile(hostedEntry: ExtractedEntry, filename: Path? = null) {
    val target = let {
        val path = hostedEntry.dir.toPath()
        if (filename == null) path else path.resolve(filename)
    }
    if (target.toFile().isDirectory()) {
        val entries = target.listDirectoryEntries()
        respondHtml {
            body {
                pre {
                    entries.forEach { file ->
                        a(href = request.uri + "/" + file.name) {
                            +file.name
                            if (file.isDirectory()) {
                                +" <dir>"
                            }
                        }
                        +"\n"
                    }
                }
            }
        }
    } else {
        respondFileShow(target.toFile())
    }
}
