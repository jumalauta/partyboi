package party.jml.partyboi.screen.slides

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.assets.Asset
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Label
import party.jml.partyboi.screen.SlideType
import party.jml.partyboi.validation.Validateable

@Serializable
data class ImageSlide(
    @Label("Image")
    val assetImage: String,
) : Slide<ImageSlide>, Validateable<ImageSlide> {
    override suspend fun render(ctx: FlowContent, app: AppServices) {
        with(ctx) {
            div(classes = "image") {
                attributes["style"] = "background-image: url(\"/assets/$assetImage\")"
            }
        }
    }

    override fun getForm(): Form<ImageSlide> = Form(ImageSlide::class, this, true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = Asset(assetImage).displayName
    override fun getType(): SlideType = SlideType("image", "Image")

    companion object {
        val Empty = ImageSlide("")
    }
}