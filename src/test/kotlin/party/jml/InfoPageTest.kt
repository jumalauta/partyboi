package party.jml

import arrow.core.raise.either
import it.skrape.matchers.toBe
import it.skrape.selects.html5.article
import party.jml.partyboi.AppServices
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.screen.slides.TextSlide
import java.time.LocalDateTime
import kotlin.test.Test

class InfoPageTest : PartyboiTester {
    @Test
    fun testEmptyInfoPage() = test {
        services { app -> resetInfoPage(app).bind() }

        it.get("/") {
            article {
                findFirst { text.toBe("Nothing to share yet...") }
                findSecond { text.toBe("Schedule will be released soon!") }
            }
        }
    }

    @Test
    fun testInfoNuggets() = test {
        services { app ->
            resetInfoPage(app).bind()

            val slide1 = app.screen.addSlide("default", TextSlide("Hello, world!", "Nice to be here!")).bind()
            app.screen.showOnInfo(slide1.id, true).bind()

            app.screen.addSlide("default", TextSlide("Food wave", "Food wave begins at 14:00")).bind()
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
        services { app ->
            resetInfoPage(app).bind()
            app.events.add(
                NewEvent(
                    name = "Foodwave",
                    time = LocalDateTime.of(2025, 2, 18, 20, 8),
                    visible = true
                )
            ).bind()
            app.events.add(
                NewEvent(
                    name = "Secret santa",
                    time = LocalDateTime.of(2025, 2, 20, 20, 8),
                    visible = false
                ),
            ).bind()
        }

        it.get("/") {
            article {
                findFirst { text.toBe("Nothing to share yet...") }
                findSecond { text.toBe("Tuesday 2025-02-18 20:08 Foodwave") }
            }
        }
    }

    private fun resetInfoPage(app: AppServices) = either {
        app.screen.deleteAll().bind()
        app.events.deleteAll().bind()
    }
}
