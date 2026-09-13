package party.jml.partyboi.infoscreen

import arrow.core.right
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import party.jml.partyboi.AppServices
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.infoscreen.slides.*
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.EventRepository
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.TimeService
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
    val slides = getExampleSlides()

    val initialPage = RenderedExampleSlide(
        name = "__INIT__",
        content = InfoScreenPage.render(slides.first().slide, mockApp)
    )

    return listOf(initialPage) + getExampleSlides().map {
        RenderedExampleSlide(
            name = it.name,
            content = InfoScreenPage.renderContent(it.slide, mockApp),
        )
    }
}

fun getExampleSlides() = listOf(
    ExampleSlide(
        "Short info",
        TextSlide(
            "Welcome to the party!",
            "Lovely to see you."
        )
    ),
    ExampleSlide(
        "Text & list",
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
        "Wall of text",
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
        "Image",
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
        "QR Code & text",
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
        "Starts soon",
        TextSlide.compoStartsSoon("Combined Demo")
    ),
    ExampleSlide(
        "Compo entry",
        TextSlide.compoSlide(
            0,
            Entry(
                id = UUID.randomUUID(),
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
            """.trimIndent(),
                orgComment = null,
                compoId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                qualified = true,
                runOrder = 0,
                timestamp = Instant.DISTANT_PAST,
                allowEdit = false,
                duration = null,
            ),
            hideAuthor = false,
        )
    ),
    ExampleSlide(
        "Compo entry + chips",
        TextSlide.compoSlide(
            1,
            Entry(
                id = UUID.randomUUID(),
                title = "Neural Nightdrive",
                author = "Jumalauta",
                screenComment = "Made in a hurry on the bus. All greetings to everyone!",
                orgComment = null,
                compoId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                qualified = true,
                runOrder = 1,
                timestamp = Instant.DISTANT_PAST,
                allowEdit = false,
                duration = null,
                remote = true,
                aiGenerated = true,
            ),
            hideAuthor = false,
        )
    ),
    ExampleSlide(
        "Compo has ended",
        TextSlide.compoHasEnded("Combined Demo")
    ),
    ExampleSlide(
        "Timer",
        TimerSlide(
            message = "Voting for **Combined Demo** closes soon!",
            endsAtEpochMs = Clock.System.now().plus(5.minutes).toEpochMilliseconds(),
        )
    ),
    ExampleSlide(
        "Timer at zero",
        TimerSlide(
            message = "Voting for **Combined Demo** has closed.",
            endsAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            finished = true,
        )
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
                month = date.month,
                day = date.day,
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
                id = UUID.randomUUID(),
                name = name,
                startTime = time(10 + index / 2, 30 * (index % 2)),
                endTime = null,
                visible = true,
            )
        }.right()
    }
}