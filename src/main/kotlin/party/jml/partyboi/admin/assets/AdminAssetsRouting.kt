package party.jml.partyboi.admin.assets

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.data.receiveForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.RedirectPage
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage

fun Application.configureAdminAssetsRouting(app: AppServices) {
    routing {
        authenticate("admin") {
            get("/admin/assets") {
                val assets = app.assets.getList()
                val addAsset = Form(AdminAssetsPage.AddAsset::class, AdminAssetsPage.AddAsset.Empty, initial = true)
                call.respondPage(AdminAssetsPage.render(assets, addAsset))
            }

            post("/admin/assets") {
                val upload = call.receiveForm<AdminAssetsPage.AddAsset>()
                call.respondEither({
                    either {
                        val validated = upload.bind().validated().bind()
                        app.assets.write(validated.file).bind()
                        RedirectPage("/admin/assets")
                    }

                }, { error ->
                    either {
                        val assets = app.assets.getList()
                        AdminAssetsPage.render(assets, upload.bind().with(error))
                    }
                })
            }
        }

        authenticate("admin", optional = true) {
            delete("/admin/assets/{file}") {
                call.apiRespond {
                    either {
                        call.userSession().bind()
                        val file = call.parameterString("file").bind()
                        app.assets.delete(file).bind()
                    }
                }
            }
        }
    }
}