package party.jml.partyboi.screen.slides

import kotlinx.html.FlowContent
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.Form
import party.jml.partyboi.screen.SlideType
import party.jml.partyboi.validation.Validateable

@Serializable
sealed interface Slide<A : Validateable<A>> {
    suspend fun render(ctx: FlowContent, app: AppServices)
    fun variant(): String? = null
    fun getForm(): Form<A>
    fun toJson(): String
    fun getName(): String
    fun getType(): SlideType
}
