package party.jml.partyboi.assets.admin

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterPathString
import party.jml.partyboi.form.collect
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondPage

fun Application.configureAdminAssetsRouting(app: AppServices) {
    adminRouting {
        val redirectionToAssets = Redirection("/admin/assets")

        get("/admin/assets") {
            call.respondPage(AdminAssetsPage.render(assets = app.assets.getList()))
        }

        post("/admin/assets") {
            val (_, files) = call.receiveMultipart(app.config.maxFileUploadSize).collect()
            val uploadedFiles = (files["files"] ?: emptyList()).filter { it.isDefined }

            if (uploadedFiles.isEmpty()) {
                call.respondPage(AdminAssetsPage.render(
                    assets = app.assets.getList(),
                    error = "No files selected",
                ))
                return@post
            }

            val errors = uploadedFiles.mapNotNull { file ->
                app.assets.write(file).fold({ "${file.name}: ${it.message}" }, { null })
            }

            if (errors.isNotEmpty()) {
                call.respondPage(AdminAssetsPage.render(
                    assets = app.assets.getList(),
                    error = errors.joinToString("; "),
                ))
            } else {
                call.respondPage(redirectionToAssets)
            }
        }
    }

    adminApiRouting {
        delete("/admin/assets/{file...}") {
            call.apiRespond {
                call.userSession(app).bind()
                val file = call.parameterPathString("file").bind()
                app.assets.delete(file).bind()
            }
        }

        delete("/admin/assets-dir/{dir...}") {
            call.apiRespond {
                call.userSession(app).bind()
                val dir = call.parameterPathString("dir").bind()
                app.assets.deleteDirectory(dir).bind()
            }
        }
    }
}