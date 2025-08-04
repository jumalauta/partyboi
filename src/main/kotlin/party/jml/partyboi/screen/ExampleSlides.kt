package party.jml.partyboi.screen

import arrow.core.right
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
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
    return getExampleSlides().map {
        RenderedExampleSlide(
            name = it.name,
            content = ScreenPage.render(it.slide, theme, mockApp),
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
        ImageSlide("/assets/example.jpg")
    ),
    ExampleSlide(
        "QR Code",
        QrCodeSlide(
            "Food wave",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ",
            "Order food by scanning the following QR Code before 14:00!"
        )
    ),
    ExampleSlide(
        "QR Code with too much text",
        QrCodeSlide(
            "Lorem ipsum dolor sit amet",
            "https://www.youtube.com/watch?v=RMSIBvcnrJc&list=RDRMSIBvcnrJc",
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
    )
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