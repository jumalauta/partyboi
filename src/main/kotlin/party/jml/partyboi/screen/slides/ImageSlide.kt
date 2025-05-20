package party.jml.partyboi.screen.slides

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.Slide
import party.jml.partyboi.screen.SlideType

@Serializable
data class ImageSlide(
    @property:Field(order = 0, label = "Image")
    val assetImage: String,
) : Slide<ImageSlide>, Validateable<ImageSlide> {
    override fun render(ctx: FlowContent, app: AppServices) {
        with(ctx) {
            div(classes = "image") {
                attributes["style"] = "background-image: url(\"/assets/uploaded/$assetImage\")"
            }
        }
    }

    override fun getForm(): Form<ImageSlide> = Form(ImageSlide::class, this, true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = assetImage
    override fun getType(): SlideType = SlideType("image", "Image")

    companion object {
        val Empty = ImageSlide("")
    }
}