package party.jml.partyboi.admin.screen

import party.jml.partyboi.templates.Page
import kotlinx.html.*
import party.jml.partyboi.screen.ScreenRow
import party.jml.partyboi.screen.ScreenState
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.icon
import party.jml.partyboi.templates.components.reloadSection

object ScreenPresentation {
    fun render(slideSet: String, slides: List<ScreenRow>, state: ScreenState, isRunning: Boolean) =
        Page("Screen presentation mode") {
            columns(
                { article {
                    header { +"Preview" }
                    div(classes = "screen-preview-container") {
                        iframe(classes = "screen-preview") {
                            attributes.put("src", "/screen")
                            attributes.put("width", "1920")
                            attributes.put("height", "1080")
                        }
                    }
                } },
                { reloadSection {
                    fieldSet {
                        attributes.put("role", "group")
                        if (state.slideSet == slideSet) {
                            postButton("/admin/screen/${slideSet}/presentation/next") {
                                icon("forward-step")
                                +" Next"
                            }
                        } else {
                            postButton("/admin/screen/${slideSet}/presentation/start") {
                                icon("play")
                                +" Start"
                            }
                        }
                    }

                    article {
                        header { +"Slides" }
                        table {
                            thead {
                                tr {
                                    th(classes = "narrow") { +"#" }
                                    th { +"Name" }
                                }
                            }
                            tbody {
                                slides.forEachIndexed { index, slideRow ->
                                    val slide = slideRow.getSlide()
                                    tr {
                                        td { +(index + 1).toString() }
                                        td { +slide.getName() }
                                    }
                                }
                            }
                        }
                    }
                } }
            )


            script {
                unsafe { raw("""
                    const resizePreview = () => {
                        const container = document.querySelector(".screen-preview-container") 
                        const frame = document.querySelector(".screen-preview")
                        const ratio = container.offsetWidth / frame.offsetWidth
                        const scale = ratio * 100 + "%"
                        const height = frame.offsetHeight * ratio 
                        container.style.transform = "scale(" + scale + ")"
                        container.style.transformOrigin = "top left"
                        container.style.height = height + "px"
                    }
                    
                    resizePreview()
                    window.addEventListener("resize", resizePreview) 
                """.trimIndent()) }
            }
        }
}