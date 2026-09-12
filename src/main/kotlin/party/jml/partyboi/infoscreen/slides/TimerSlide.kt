package party.jml.partyboi.infoscreen.slides

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.form.Form
import party.jml.partyboi.infoscreen.AutoRunHalting
import party.jml.partyboi.infoscreen.NonEditable
import party.jml.partyboi.infoscreen.SlideType
import party.jml.partyboi.templates.components.markdown
import party.jml.partyboi.validation.Validateable
import kotlin.time.Clock

@Serializable
data class TimerSlide(
    val message: String = "",
    val endsAtEpochMs: Long? = null,
    val remainingMs: Long? = null,
    val flashThresholdSecs: Int = 60,
    val finished: Boolean = false,
) : Slide<TimerSlide>, Validateable<TimerSlide>, AutoRunHalting, NonEditable {
    override suspend fun render(ctx: FlowContent, app: AppServices) {
        with(ctx) {
            div(classes = "countdown") {
                // The screen client swaps slides with innerHTML, so inline scripts never run;
                // screen.js drives the ticking and flashing off these data attributes instead.
                // data-server-now lets the client correct for its own clock skew.
                attributes["data-server-now"] = Clock.System.now().toEpochMilliseconds().toString()
                endsAtEpochMs?.let { attributes["data-ends-at"] = it.toString() }
                remainingMs?.let { attributes["data-remaining-ms"] = it.toString() }
                attributes["data-flash-threshold"] = flashThresholdSecs.toString()
                if (finished) attributes["data-finished"] = "true"
                span(classes = "countdown-time") { +initialDisplay() }
                if (remainingMs != null && !finished) {
                    div(classes = "countdown-paused-label") { +"PAUSED" }
                }
            }
            if (message.isNotBlank()) {
                markdown(message)
            }
        }
    }

    override fun haltAutoRun(): Boolean = true
    override fun getForm(): Form<TimerSlide> = Form(TimerSlide::class, this, true)
    override fun toJson(): String = Json.encodeToString(this)
    override fun getName(): String = "Countdown timer"
    override fun getType(): SlideType = SlideType("stopwatch", "Countdown timer")

    // Server-rendered fallback shown until (and in case) the client script kicks in.
    private fun initialDisplay(): String {
        val remaining = remainingMs
            ?: endsAtEpochMs?.let { it - Clock.System.now().toEpochMilliseconds() }
            ?: 0L
        return if (finished || (remainingMs == null && remaining <= 0)) TIMES_UP
        else formatRemaining(remaining)
    }

    companion object {
        const val TIMES_UP = "Time's up!"

        fun formatRemaining(ms: Long): String {
            val totalSecs = (ms.coerceAtLeast(0) + 999) / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
    }
}
