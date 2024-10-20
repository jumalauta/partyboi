package party.jml.partyboi.screen.slides

import kotlinx.html.FlowContent
import kotlinx.html.h1
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.Slide
import party.jml.partyboi.templates.components.markdown

@Serializable
data class TextSlide (
    @property:Field(order = 0, label = "Title")
    val title: String,
    @property:Field(order = 1, label = "Content", large = true)
    val content: String,
) : Slide<TextSlide>, Validateable<TextSlide> {
    override fun render(ctx: FlowContent) {
        with(ctx) {
            h1 { +title }
            markdown(content)
        }
    }

    override fun getForm(): Form<TextSlide> = Form(TextSlide::class, this, true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = title

    companion object {
        val Empty = TextSlide("", "")
    }
}