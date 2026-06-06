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
import party.jml.partyboi.compos.*
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
            manualResultCounts = app.manualResults.countByCompo().bind(),
        )
    }

    suspend fun renderAdminEditCompoPage(
        compoId: AppResult<UUID>,
        activeTab: CompoTab = CompoTab.SETTINGS,
        compoForm: Form<Compo>? = null,
        manualResultForm: Form<NewManualResult>? = null,
        editManualResultId: String? = null,
    ) = either {
        val id = compoId.bind()
        val compo = compoForm?.data ?: app.compos.getById(id).bind()
        // Entries / results are needed for the second tab's badge count regardless of
        // which tab is active; the heavier per-entry file & preview lookups are only
        // needed to render the entries table itself.
        val entries = if (compo.manualResults) emptyList() else app.entries.getEntriesForCompo(id).bind()
        val showEntries = activeTab == CompoTab.ENTRIES && !compo.manualResults
        val files = if (showEntries) app.entries.getLatestFilesByCompo(id).bind() else emptyMap()
        val previews = if (showEntries) app.previews.getEntryPreviews(entries) else emptyList()
        AdminEditCompoPage.render(
            compoForm = compoForm ?: Form(Compo::class, compo, initial = true),
            entries = entries,
            compos = app.compos.getAllCompos().bind(),
            files = files,
            previews = previews.associateBy { it.entryId },
            manualResults = if (compo.manualResults) app.manualResults.getByCompoId(id).bind() else emptyList(),
            manualResultForm = manualResultForm,
            editManualResultId = editManualResultId,
            activeTab = activeTab,
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
                renderAdminEditCompoPage(call.parameterUUID("id"), CompoTab.SETTINGS).bind()
            }
        }

        // Second tab (entries for normal compos, results for manual ones). Both render the
        // same tab; the page branches on the compo type.
        get("/admin/compos/{id}/entries") {
            call.respondEither {
                renderAdminEditCompoPage(call.parameterUUID("id"), CompoTab.ENTRIES).bind()
            }
        }

        get("/admin/compos/{id}/results") {
            call.respondEither {
                renderAdminEditCompoPage(call.parameterUUID("id"), CompoTab.ENTRIES).bind()
            }
        }

        post("/admin/compos/{id}") {
            call.processForm<Compo>(
                { compo -> app.compos.update(compo).map { Redirection("/admin/compos/${compo.id}") }.bind() },
                { renderAdminEditCompoPage(call.parameterUUID("id"), CompoTab.SETTINGS, compoForm = it).bind() }
            )
        }

        post("/admin/compos/{id}/manual-results") {
            val compoId = call.parameterUUID("id")
            val redirectToResults = Redirection("/admin/compos/${call.parameters["id"]}/results")
            call.processForm<NewManualResult>(
                {
                    val id = compoId.bind()
                    app.manualResults.add(it.copy(compoId = id)).bind()
                    redirectToResults
                },
                { renderAdminEditCompoPage(compoId, CompoTab.ENTRIES, manualResultForm = it).bind() }
            )
        }

        get("/admin/compos/{id}/manual-results/{rid}") {
            call.respondEither {
                val compoId = call.parameterUUID("id")
                val cid = compoId.bind()
                val resultId = call.parameterUUID("rid").bind()
                val result = app.manualResults.getByCompoId(cid).bind()
                    .find { it.id == resultId } ?: raise(NotFound("Manual result"))
                renderAdminEditCompoPage(
                    compoId = compoId,
                    activeTab = CompoTab.ENTRIES,
                    manualResultForm = Form(
                        NewManualResult::class,
                        NewManualResult(
                            title = result.title,
                            author = result.author,
                            scoreText = result.scoreText,
                            screenComment = result.screenComment ?: "",
                            compoId = cid,
                        ),
                        initial = true,
                    ),
                    editManualResultId = resultId.toString(),
                ).bind()
            }
        }

        post("/admin/compos/{id}/manual-results/{rid}") {
            val compoId = call.parameterUUID("id")
            val resultId = call.parameterUUID("rid")
            val redirectToResults = Redirection("/admin/compos/${call.parameters["id"]}/results")
            call.processForm<NewManualResult>(
                {
                    val cid = compoId.bind()
                    val rid = resultId.bind()
                    val existing = app.manualResults.getByCompoId(cid).bind()
                        .find { it.id == rid } ?: raise(NotFound("Manual result"))
                    app.manualResults.update(
                        ManualResult(
                            id = rid,
                            compoId = cid,
                            title = it.title,
                            author = it.author,
                            scoreText = it.scoreText,
                            screenComment = it.screenComment.takeIf { s -> s.isNotBlank() },
                            position = existing.position,
                        )
                    ).bind()
                    redirectToResults
                },
                { formWithErrors ->
                    renderAdminEditCompoPage(
                        compoId = compoId,
                        activeTab = CompoTab.ENTRIES,
                        manualResultForm = formWithErrors,
                        editManualResultId = call.parameters["rid"],
                    ).bind()
                }
            )
        }

        get("/admin/compos/{id}/run") {
            call.respondEither {
                val compoId = call.parameterUUID("id").bind()
                val compo = app.compos.getById(compoId).bind()
                val steps = app.compoRun.initCompoSteps(compo).bind()

                CompoScreenRunningPage.render(
                    compo = compo,
                    steps = steps,
                )
            }
        }

        post("/admin/compos/{id}/run/next") {
            call.apiRespond {
                // TODO: Verify that the compo id matches the current compo
                app.compoRun.nextCompoStep().bind()
            }
        }

        post("/admin/compos/{id}/run/prev") {
            call.apiRespond {
                // TODO: Verify that the compo id matches the current compo
                app.compoRun.prevCompoStep().bind()
            }
        }

        get("/admin/compos/{id}/run-results") {
            call.respondEither {
                val compoId = call.parameterUUID("id").bind()
                val compo = app.compos.getById(compoId).bind()
                val steps = app.resultsRun.initResultsSteps(compo).bind()

                ResultsScreenRunningPage.render(
                    compo = compo,
                    steps = steps,
                )
            }
        }

        post("/admin/compos/{id}/run-results/next") {
            call.apiRespond {
                app.resultsRun.nextResultsStep().bind()
            }
        }

        post("/admin/compos/{id}/run-results/prev") {
            call.apiRespond {
                app.resultsRun.prevResultsStep().bind()
            }
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
                val pkg = app.compoRun.compressAllEntries().bind()
                call.respondNamedFileDownload(pkg.file, "${pkg.name}.zip")
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
            call.switchApiUuid { id, state -> app.compos.setVisible(id, state) }
        }

        put("/admin/compos/{id}/setSubmit/{state}") {
            call.switchApiUuid { id, state ->
                either {
                    if (state) {
                        app.compos.allowVoting(id, false).bind()
                        app.compos.publishResults(id, false).bind()
                        app.compos.setVisible(id, true).bind()
                    }
                    app.compos.allowSubmit(id, state).bind()
                }
            }
        }

        put("/admin/compos/{id}/setVoting/{state}") {
            call.switchApiUuid { id, state ->
                either {
                    if (state) {
                        app.compos.allowSubmit(id, false).bind()
                        app.compos.publishResults(id, false).bind()
                        app.compos.setVisible(id, true).bind()
                    }
                    app.compos.allowVoting(id, state).bind()
                }
            }
        }

        put("/admin/compos/{id}/publishResults/{state}") {
            call.switchApiUuid { id, state ->
                either {
                    if (state) {
                        app.compos.allowSubmit(id, false).bind()
                        app.compos.allowVoting(id, false).bind()
                        app.compos.setVisible(id, true).bind()
                    }
                    app.compos.publishResults(id, state).bind()
                }
            }
        }

        put("/admin/compos/entries/{id}/setQualified/{state}") {
            call.switchApiUuid { id, state -> app.entries.setQualified(id, state) }
        }

        put("/admin/compos/entries/{id}/allowEdit/{state}") {
            call.switchApiUuid { id, state -> app.entries.allowEdit(id, state) }
        }

        put("/admin/compos/entries/{id}/duration/{seconds}") {
            call.apiRespond {
                val id = call.parameterUUID("id").bind()
                val secondsParam = call.parameterString("seconds").bind()
                val duration = if (secondsParam == "none") null
                else secondsParam.toDoubleOrNull() ?: raise(InvalidInput("seconds"))
                app.entries.setDuration(id, duration).bind()
            }
        }

        delete("/admin/compos/entries/{id}") {
            call.apiRespond {
                val id = call.parameterUUID("id").bind()
                app.entries.delete(id).bind()
            }
        }

        delete("/admin/compos/{id}/manual-results/{rid}") {
            call.apiRespond {
                val resultId = call.parameterUUID("rid").bind()
                app.manualResults.delete(resultId).bind()
            }
        }

        post("/admin/compos/{id}/manual-results/order") {
            either {
                val order = call.receive<List<String>>()
                order
                    .mapIndexed { index, rid -> app.manualResults.setPosition(UUID.fromString(rid), index + 1) }
                    .bindAll()
                call.respondText("OK")
            }
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
    val baseDir = hostedEntry.dir.toPath().normalize()
    val target = if (filename == null) baseDir else baseDir.resolve(filename).normalize()
    // Reject paths that escape the extracted-entry directory (e.g. ../../etc/passwd or an absolute
    // path) before touching the filesystem.
    if (!target.startsWith(baseDir)) {
        respondPage(NotFound("File not found"))
        return
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
