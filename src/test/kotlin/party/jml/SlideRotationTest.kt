package party.jml

import party.jml.partyboi.infoscreen.SlideRotation
import party.jml.partyboi.infoscreen.SlideRow
import party.jml.partyboi.infoscreen.slides.ImageSlide
import party.jml.partyboi.infoscreen.slides.TextSlide
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlideRotationTest {
    private var order = 0

    private fun slide(type: String, visible: Boolean) = SlideRow(
        id = UUID.randomUUID(),
        slideSet = "test",
        type = type,
        content = "{}",
        visible = visible,
        runOrder = order++,
        showOnInfoPage = false,
        readOnly = false,
    )

    private fun text(visible: Boolean = true) = slide(TextSlide::class.java.name, visible)
    private fun image(visible: Boolean = true) = slide(ImageSlide::class.java.name, visible)

    // Runs the rotation the way InfoScreenService does: feed the shown slide's id and the
    // returned cursor back in, collecting the ids of the slides shown.
    private fun rotate(
        slides: List<SlideRow>,
        maxImageSlides: Int?,
        count: Int,
        startCursor: Int = 0,
    ): Pair<List<SlideRow>, Int> {
        var currentId: UUID? = null
        var cursor = startCursor
        val shown = mutableListOf<SlideRow>()
        repeat(count) {
            val next = SlideRotation.next(slides, currentId, maxImageSlides, cursor)
                .fold({ throw AssertionError("Rotation failed after ${shown.size} slides: $it") }, { it })
            shown.add(next.slide)
            currentId = next.slide.id
            cursor = next.cursor
        }
        return Pair(shown, cursor)
    }

    @Test
    fun testUnlimitedReproducesPlainRotation() {
        val t1 = text()
        val hidden = text(visible = false)
        val i1 = image()
        val t2 = text()
        val slides = listOf(t1, hidden, i1, t2)

        val (shown, cursor) = rotate(slides, maxImageSlides = null, count = 7)
        assertEquals(
            listOf(t1, i1, t2, t1, i1, t2, t1).map { it.id },
            shown.map { it.id },
        )
        assertEquals(0, cursor)
    }

    @Test
    fun testUnknownCurrentIdIsAnError() {
        val slides = listOf(text(), image())
        assertTrue(SlideRotation.next(slides, UUID.randomUUID(), null, 0).isLeft())
    }

    @Test
    fun testNoVisibleSlidesIsAnError() {
        val slides = listOf(text(visible = false), image(visible = false))
        assertTrue(SlideRotation.next(slides, null, null, 0).isLeft())
    }

    @Test
    fun testThrottledPassesRoundRobinThroughImages() {
        val t1 = text()
        val images = List(8) { image() }
        val t2 = text()
        val slides = listOf(t1) + images + t2

        // Pass 1: both text slides plus the first window of 3 images.
        val (pass1, cursor1) = rotate(slides, maxImageSlides = 3, count = 5)
        assertEquals(
            listOf(t1, images[0], images[1], images[2], t2).map { it.id },
            pass1.map { it.id },
        )
        assertEquals(0, cursor1)

        // Pass 2 continues with the next window; the cursor advances at the wrap into
        // each pass, so mid-pass it holds the current pass's window start.
        val (twoPasses, cursor2) = rotate(slides, maxImageSlides = 3, count = 10)
        assertEquals(
            (listOf(t1, images[0], images[1], images[2], t2) +
                    listOf(t1, images[3], images[4], images[5], t2)).map { it.id },
            twoPasses.map { it.id },
        )
        assertEquals(3, cursor2)

        // Pass 3's window wraps around the image pool: {i7, i8, i1}, shown in run order.
        val (threePasses, cursor3) = rotate(slides, maxImageSlides = 3, count = 15)
        assertEquals(
            (twoPasses + listOf(t1, images[0], images[6], images[7], t2)).map { it.id },
            threePasses.map { it.id },
        )
        assertEquals(6, cursor3)
    }

    @Test
    fun testAllImageSetDoesNotStall() {
        val images = List(8) { image() }

        val (shown, _) = rotate(images, maxImageSlides = 3, count = 24)
        // Never more than the window size before the window moves on, and every image
        // gets airtime.
        assertEquals(images.map { it.id }.toSet(), shown.map { it.id }.toSet())
    }

    @Test
    fun testZeroHidesAllImages() {
        val t1 = text()
        val i1 = image()
        val i2 = image()
        val t2 = text()
        val slides = listOf(t1, i1, i2, t2)

        val (shown, _) = rotate(slides, maxImageSlides = 0, count = 6)
        assertEquals(
            listOf(t1, t2, t1, t2, t1, t2).map { it.id },
            shown.map { it.id },
        )
    }

    @Test
    fun testZeroWithOnlyImagesIsAnError() {
        val slides = listOf(image(), image())
        assertTrue(SlideRotation.next(slides, null, 0, 0).isLeft())
    }

    @Test
    fun testLimitAtLeastPoolSizeBehavesAsUnlimited() {
        val t1 = text()
        val i1 = image()
        val i2 = image()
        val slides = listOf(t1, i1, i2)

        val (shown, cursor) = rotate(slides, maxImageSlides = 2, count = 6, startCursor = 1)
        assertEquals(
            listOf(t1, i1, i2, t1, i1, i2).map { it.id },
            shown.map { it.id },
        )
        assertEquals(1, cursor, "cursor must not move when the limit is not in effect")
    }

    @Test
    fun testInvisibleImagesAreExcludedFromThePool() {
        val t1 = text()
        val hidden = image(visible = false)
        val i1 = image()
        val i2 = image()
        val slides = listOf(t1, hidden, i1, i2)

        val (shown, _) = rotate(slides, maxImageSlides = 1, count = 6)
        assertEquals(
            listOf(t1, i1, t1, i2, t1, i1).map { it.id },
            shown.map { it.id },
        )
    }
}
