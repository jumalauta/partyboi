package party.jml.partyboi.infoscreen

import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.data.Numbers.positiveIntOrNull
import party.jml.partyboi.infoscreen.slides.ImageSlide
import party.jml.partyboi.system.AppResult
import java.util.*

// Selects the next slide to show when rotating through a slide set. Image slides are
// low priority: when the slide set has maxImageSlides set, only a window of that many
// image slides is shown per rotation pass, and the window round-robins through all of
// the set's visible image slides so every one of them eventually gets airtime.
//
// Pure and stateless so it can be unit tested without a database; the caller owns the
// cursor (the round-robin offset into the visible image slides) and must store the
// cursor returned in Next and pass it back on subsequent calls.
object SlideRotation {
    data class Next(val slide: SlideRow, val cursor: Int)

    private val imageSlideType = ImageSlide::class.java.name

    private val SlideRow.isImage get() = type == imageSlideType

    // slides: the full slide set in run order. currentId == null means the set is being
    // (re)started from the top. maxImageSlides == null means no limit.
    fun next(slides: List<SlideRow>, currentId: UUID?, maxImageSlides: Int?, cursor: Int): AppResult<Next> =
        either {
            val index = currentId?.let { id ->
                ensureNotNull(positiveIntOrNull(slides.indexOfFirst { it.id == id })) {
                    InvalidInput("$id not in slide set")
                }
            }
            val afterCurrent = if (index == null) slides else slides.slice((index + 1)..<slides.size)
            val images = slides.filter { it.visible && it.isImage }
            val m = images.size
            val n = maxImageSlides

            if (n == null || n >= m) {
                // No throttling needed (also covers m == 0): rotate at the current slide,
                // take the first visible one, leave the cursor untouched.
                val rotated = if (index == null) slides else afterCurrent + slides.slice(0..index)
                val slide = ensureNotNull(rotated.firstOrNull { it.visible }) {
                    InvalidInput("No visible slides in slide set")
                }
                return@either Next(slide, cursor)
            }

            // Pass 0 walks the slides after the current one under the current image window;
            // each further pass k walks the whole list under the next window. The window
            // start orbit has period <= m, so m + 1 passes are enough to reach every image;
            // if nothing matches by then there is nothing showable (e.g. n == 0 and no
            // visible non-image slides).
            for (k in 0..m + 1) {
                val candidates = if (k == 0) afterCurrent else slides
                val windowStart = cursor + k * n
                val allowed = allowedIds(images, n, windowStart)
                val slide = candidates.firstOrNull { it.visible && (!it.isImage || it.id in allowed) }
                if (slide != null) {
                    return@either Next(slide, if (k == 0) cursor else windowStart.mod(m))
                }
            }
            raise(InvalidInput("No visible slides in slide set"))
        }

    // The image slides allowed in the pass whose window starts at windowStart (round-robin).
    private fun allowedIds(images: List<SlideRow>, n: Int, windowStart: Int): Set<UUID> =
        (0..<n).map { images[(windowStart + it).mod(images.size)].id }.toSet()
}
