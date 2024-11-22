package party.jml.partyboi.admin.assets

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminApiRouting
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.auth.userSession
import party.jml.partyboi.data.apiRespond
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondPage

fun Application.configureAdminAssetsRouting(app: AppServices) {
    fun renderAdminAssetsPage(addAssetForm: Form<AdminAssetsPage.AddAsset>? = null) =
        AdminAssetsPage.render(
            addAssetForm = addAssetForm ?: Form(
                AdminAssetsPage.AddAsset::class,
                AdminAssetsPage.AddAsset.Empty,
                initial = true
            ),
            assets = app.assets.getList(),
        )

    adminRouting {
        val redirectionToAssets = Redirection("/admin/assets")

        get("/admin/assets") {
            call.respondPage(renderAdminAssetsPage())
        }

        post("/admin/assets") {
            call.processForm<AdminAssetsPage.AddAsset>(
                { app.assets.write(it.file).map { redirectionToAssets } },
                { renderAdminAssetsPage(addAssetForm = it).right() }
            )
        }
    }

    adminApiRouting {
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