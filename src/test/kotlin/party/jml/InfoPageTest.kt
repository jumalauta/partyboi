package party.jml

import arrow.core.raise.either
import it.skrape.matchers.toBe
import it.skrape.selects.html5.article
import kotlinx.datetime.LocalDateTime
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.screen.slides.TextSlide
import kotlin.test.Test

class InfoPageTest : PartyboiTester {
    @Test
    fun testEmptyInfoPage() = test {
        it.get("/") {
            article {
                findFirst { text.toBe("Nothing to share yet...") }
                findSecond { text.toBe("Schedule will be released soon!") }
            }
        }
    }

    @Test
    fun testInfoNuggets() = test {
        setupServices {
            either {
                val slide1 = screen.addSlide("default", TextSlide("Hello, world!", "Nice to be here!")).bind()
                screen.showOnInfo(slide1.id, true).bind()
                screen.addSlide("default", TextSlide("Food wave", "Food wave begins at 14:00")).bind()
            }
        }

        it.get("/") {
            article {
                findFirst { text.toBe("Hello, world! Nice to be here!") }
                findSecond { text.toBe("Schedule will be released soon!") }
            }
        }
    }

    @Test
    fun testSchedule() = test {
        setupServices {
            either {
                events.add(
                    NewEvent(
                        name = "Foodwave",
                        startTime = LocalDateTime(2025, 2, 18, 20, 8),
                        visible = true
                    )
                ).bind()
                events.add(
                    NewEvent(
                        name = "Secret santa",
                        startTime = LocalDateTime(2025, 2, 20, 20, 8),
                        visible = false
                    ),
                ).bind()
            }
        }

        it.get("/") {
            article {
                findFirst { text.toBe("Nothing to share yet...") }
                findSecond { text.toBe("Tuesday 2025-02-18 20:08 Foodwave") }
            }
        }
    }
}
