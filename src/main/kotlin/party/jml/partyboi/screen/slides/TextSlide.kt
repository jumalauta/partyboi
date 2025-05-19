package party.jml.partyboi.screen.slides

import kotlinx.html.FlowContent
import kotlinx.html.h1
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.Slide
import party.jml.partyboi.screen.SlideType
import party.jml.partyboi.templates.components.markdown

@Serializable
data class TextSlide(
    @property:Field(order = 0, label = "Title")
    val title: String,
    @property:Field(order = 1, label = "Content", presentation = FieldPresentation.large)
    val content: String,
    @property:Field(order = 2, label = "Variant", presentation = FieldPresentation.hidden)
    val variant: String? = null,
) : Slide<TextSlide>, Validateable<TextSlide> {
    override fun render(ctx: FlowContent) {
        with(ctx) {
            h1 { +title }
            markdown(content)
        }
    }

    override fun variant(): String? = variant
    override fun getForm(): Form<TextSlide> = Form(TextSlide::class, this, true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = title
    override fun getType(): SlideType = SlideType("list-ul", "Text")

    companion object {
        val Empty = TextSlide("", "", null)
        
        val CompoInfoVariant = "compo-info"
        val CompoEntryVariant = "compo-entry"
    }
}