package party.jml.partyboi.screen

import arrow.core.none
import arrow.core.right
import arrow.core.some
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.EventRepository
import party.jml.partyboi.screen.slides.ImageSlide
import party.jml.partyboi.screen.slides.QrCodeSlide
import party.jml.partyboi.screen.slides.ScheduleSlide
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.TimeService

data class ExampleSlide(
    val name: String,
    val slide: Slide<*>,
)

@Serializable
data class RenderedExampleSlide(
    val name: String,
    val content: String,
)

fun getRenderedExampleSlides(app: AppServices): List<RenderedExampleSlide> {
    val mockApp = MockAppServices(app)
    val theme = ScreenTheme.CUSTOM
    val slides = getExampleSlides()

    val initialPage = RenderedExampleSlide(
        name = "__INIT__",
        content = ScreenPage.render(slides.first().slide, theme, mockApp)
    )

    return listOf(initialPage) + getExampleSlides().map {
        RenderedExampleSlide(
            name = it.name,
            content = ScreenPage.renderContent(it.slide, mockApp),
        )
    }
}

fun getExampleSlides() = listOf(
    ExampleSlide(
        "Short info text",
        TextSlide(
            "Welcome to the party!",
            "Lovely to see you."
        )
    ),
    ExampleSlide(
        "Text slide with a list",
        TextSlide(
            "Party wi-fi",
            """
                - SSID: PartyWifi
                - Password: hunter2
                
                The internet connection here is very slow. Please use sparingly, especially during compos.
            """.trimIndent()
        )
    ),
    ExampleSlide(
        "Text slide with too much text",
        TextSlide(
            "Lorem ipsum dolor sit amet",
            """
                Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus ex sapien vitae 
                pellentesque sem placerat. In id cursus mi pretium tellus duis convallis. Tempus leo eu 
                aenean sed diam urna tempor. Pulvinar vivamus fringilla lacus nec metus bibendum egestas. 
                Iaculis massa nisl malesuada lacinia integer nunc posuere. Ut hendrerit semper vel class 
                aptent taciti sociosqu. Ad litora torquent per conubia nostra inceptos himenaeos.
    
                Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus ex sapien vitae 
                pellentesque sem placerat. In id cursus mi pretium tellus duis convallis. Tempus leo eu 
                aenean sed diam urna tempor. Pulvinar vivamus fringilla lacus nec metus bibendum egestas. 
                Iaculis massa nisl malesuada lacinia integer nunc posuere. Ut hendrerit semper vel class 
                aptent taciti sociosqu. Ad litora torquent per conubia nostra inceptos himenaeos.
            """.trimIndent()
        )
    ),
    ExampleSlide(
        "Image slide",
        ImageSlide("example.jpg")
    ),
    ExampleSlide(
        "QR Code",
        QrCodeSlide(
            "Food wave",
            "a",
            "Order food by scanning the following QR Code before 14:00!"
        )
    ),
    ExampleSlide(
        "QR Code with too much text",
        QrCodeSlide(
            "Lorem ipsum dolor sit amet",
            "a",
            """
                Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus ex sapien vitae 
                pellentesque sem placerat. In id cursus mi pretium tellus duis convallis. Tempus leo eu 
                aenean sed diam urna tempor. Pulvinar vivamus fringilla lacus nec metus bibendum egestas. 
                Iaculis massa nisl malesuada lacinia integer nunc posuere. Ut hendrerit semper vel class 
                aptent taciti sociosqu. Ad litora torquent per conubia nostra inceptos himenaeos.
    
                Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus ex sapien vitae 
                pellentesque sem placerat. In id cursus mi pretium tellus duis convallis. Tempus leo eu 
                aenean sed diam urna tempor. Pulvinar vivamus fringilla lacus nec metus bibendum egestas. 
                Iaculis massa nisl malesuada lacinia integer nunc posuere. Ut hendrerit semper vel class 
                aptent taciti sociosqu. Ad litora torquent per conubia nostra inceptos himenaeos.
            """.trimIndent()
        )
    ),
    ExampleSlide(
        "Schedule",
        ScheduleSlide(LocalDate.fromEpochDays(0))
    ),
    ExampleSlide(
        "Compo starts soon",
        TextSlide.compoStartsSoon("Combined Demo")
    ),
    ExampleSlide(
        "Compo entry",
        TextSlide.compoSlide(
            0, Entry(
                id = 0,
                title = "Lorem Ipsum Mega Blast 2000",
                author = "Jumalauta + Matt Current",
                screenComment = """
                Lorem ipsum **dolor** sit amet consectetur adipiscing *elit*. Quisque faucibus ex sapien vitae 
                pellentesque sem placerat. In id cursus mi pretium tellus duis convallis. Tempus leo eu 
                aenean sed diam urna tempor. Pulvinar vivamus fringilla lacus nec metus bibendum egestas. 
                Iaculis massa nisl malesuada lacinia integer nunc posuere. Ut hendrerit semper vel class 
                aptent taciti sociosqu. Ad litora torquent per conubia nostra inceptos himenaeos.
                        
                - LOL
                - NSFW
                - RTFM
            """.trimIndent().some(),
                orgComment = none(),
                compoId = 0,
                userId = 0,
                qualified = true,
                runOrder = 0,
                timestamp = Instant.DISTANT_PAST,
                allowEdit = false,
            )
        )
    ),
    ExampleSlide(
        "Compo has ended",
        TextSlide.compoHasEnded("Combined Demo")
    ),
)

class MockAppServices(app: AppServices) : AppServices by app {
    override val events: EventRepository = MockEventRepository(app.events)
}

class MockEventRepository(events: EventRepository) : EventRepository by events {
    override suspend fun getBetween(since: Instant, until: Instant): AppResult<List<Event>> {
        val tz = TimeService.timeZone()
        val date = since.toLocalDateTime(tz)
        fun time(hours: Int, minutes: Int): Instant =
            LocalDateTime(
                year = date.year,
                monthNumber = date.monthNumber,
                dayOfMonth = date.dayOfMonth,
                hour = hours,
                minute = minutes
            ).toInstant(tz)

        return listOf(
            "Wake-Up Chill: Ambient DJ Set + Coffee",
            "Breakfast + Badge Hacking Session",
            "Shader Showdown Qualifiers",
            "Talk: \"Retro Hardware Demo Tricks (C64/Amiga)\"",
            "Fast Compo Theme Release (1h graphics + music)",
            "Lunch Break + Pixel Picnic",
            "Pixel Art Workshop (with guest artist)",
            "Music Compo: Tracked, Chiptune, and Streaming",
            "1-Hour Fast Compo Deadline & Showing",
            "Demo Screening: \"Best of the 2010s\"",
            "Shader Showdown Finals",
            "Dinner Time + BBQ Jam",
            "Wild Compo (video, hardware demos, etc.)",
            "Graphics Compo (hand-drawn, pixel, photo)",
            "Demo Compo (PC, Oldschool, Size-limited)",
            "Live Performance: Chiptune + VJ Set",
            "Results & Prize Giving"
        ).mapIndexed { index, name ->
            Event(
                id = index,
                name = name,
                startTime = time(10 + index / 2, 30 * (index % 2)),
                endTime = null,
                visible = true,
            )
        }.right()
    }
}