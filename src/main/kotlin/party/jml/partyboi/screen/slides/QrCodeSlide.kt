package party.jml.partyboi.screen.slides

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.img
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.form.Form
import party.jml.partyboi.qrcode.QrCode
import party.jml.partyboi.screen.Slide
import party.jml.partyboi.screen.SlideType
import party.jml.partyboi.templates.components.markdown

@Serializable
data class QrCodeSlide(
    @property:Field(order = 0, label = "Title")
    val title: String,
    @property:Field(order = 1, label = "QR Code URI")
    val qrcode: String,
    @property:Field(order = 2, label = "Description", presentation = FieldPresentation.large)
    val description: String,
) : Slide<QrCodeSlide>, Validateable<QrCodeSlide> {
    override suspend fun render(ctx: FlowContent, app: AppServices) {
        val myTitle = title
        with(ctx) {
            h1 { +myTitle }
            div(classes = "columns") {
                div(classes = "qrcode") {
                    img(src = qrCodeSrc())
                }
                div(classes = "description") {
                    markdown(description)
                }
            }
        }
    }

    override fun getForm(): Form<QrCodeSlide> = Form(QrCodeSlide::class, this, initial = true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = title
    override fun getType(): SlideType = SlideType("qrcode", "QR Code")

    fun qrCodeSrc(): String = QrCode.imageSrc(qrcode)

    companion object {
        val Empty = QrCodeSlide(
            title = "",
            qrcode = "https://github.com/ilkkahanninen/partyboi",
            description = "",
        )
    }
}