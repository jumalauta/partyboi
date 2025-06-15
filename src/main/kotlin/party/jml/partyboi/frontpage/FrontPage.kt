package party.jml.partyboi.frontpage

import kotlinx.datetime.TimeZone
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.h2
import kotlinx.html.p
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.schedule
import party.jml.partyboi.screen.ScreenRow
import party.jml.partyboi.screen.slides.QrCodeSlide
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.markdown

object FrontPage {
    fun render(events: List<Event>, infoScreen: List<ScreenRow>, timeZone: TimeZone) = Page("Home") {
        columns(
            {
                h2 { +"Info" }

                if (infoScreen.isNotEmpty()) {
                    infoScreen.forEach {
                        val slide = it.getSlide()
                        when (slide) {
                            is TextSlide -> {
                                article {
                                    cardHeader(slide.title)
                                    markdown(slide.content)
                                }
                            }

                            is QrCodeSlide -> {
                                article {
                                    cardHeader(slide.title)
                                    markdown(slide.description)
                                    p {
                                        a(href = slide.qrcode) {
                                            +slide.qrcode
                                        }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                } else {
                    article { +"Nothing to share yet..." }
                }
            },
            {
                h2 { +"Schedule" }
                if (events.isNotEmpty()) {
                    schedule(events, timeZone)
                } else {
                    article { +"Schedule will be released soon!" }
                }
            }

        )
    }
}