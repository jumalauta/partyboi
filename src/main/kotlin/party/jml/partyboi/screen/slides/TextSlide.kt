package party.jml.partyboi.screen.slides

import arrow.core.some
import kotlinx.html.FlowContent
import kotlinx.html.h1
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Hidden
import party.jml.partyboi.form.Label
import party.jml.partyboi.form.Large
import party.jml.partyboi.screen.AutoRunHalting
import party.jml.partyboi.screen.SlideType
import party.jml.partyboi.templates.components.markdown
import party.jml.partyboi.validation.Validateable

@Serializable
data class TextSlide(
    @Label("Title")
    val title: String,
    @Label("Content")
    @Large
    val content: String,
    @Label("Variant")
    @Hidden
    val variant: String? = null,
) : Slide<TextSlide>, Validateable<TextSlide>, AutoRunHalting {
    override suspend fun render(ctx: FlowContent, app: AppServices) {
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

    override fun haltAutoRun(): Boolean = variant == CompoInfoVariant || variant == CompoEntryVariant

    companion object {
        val Empty = TextSlide("", "", null)

        const val CompoInfoVariant = "compo-info"
        const val CompoEntryVariant = "compo-entry"

        fun compoSlide(index: Int, entry: Entry): TextSlide =
            TextSlide(
                "#${index + 1} ${entry.title}",
                listOf("## ${entry.author}".some(), entry.screenComment)
                    .flatMap { it.toList() }
                    .joinToString(separator = "\n\n") { it },
                CompoEntryVariant
            )

        fun compoStartsSoon(compoName: String): TextSlide =
            TextSlide("${compoName} compo starts soon", "", CompoInfoVariant)

        fun compoStartsNow(compoName: String): TextSlide =
            TextSlide("${compoName} compo starts NOW!", "", CompoInfoVariant)

        fun compoHasEnded(compoName: String): TextSlide =
            TextSlide("${compoName} compo has ended", "", CompoInfoVariant)
    }
}