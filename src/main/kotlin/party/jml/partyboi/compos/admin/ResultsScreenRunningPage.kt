package party.jml.partyboi.compos.admin

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.infoscreen.admin.AdminScreenPage.renderWithScreenMonitoring
import party.jml.partyboi.infoscreen.admin.postButton
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.icon
import party.jml.partyboi.templates.components.reloadSection

object ResultsScreenRunningPage {
    fun render(
        compo: Compo,
        steps: ResultsSteps,
    ) = Page("${compo.displayName} results screen") {
        h1 { +"${compo.displayName} results screen" }

        renderWithScreenMonitoring(true) {
            reloadSection {
                nav {
                    ul {
                        if (steps.current != null) {
                            li {
                                postButton("/admin/compos/${compo.id}/run-results/prev") {
                                    disabled = steps.current <= 0
                                    icon("arrow-left")
                                    +" Previous"
                                }
                            }
                            li {
                                postButton("/admin/compos/${compo.id}/run-results/next") {
                                    disabled = steps.current >= steps.steps.size - 1
                                    icon("arrow-right")
                                    +" Next"
                                }
                            }
                        } else {
                            li {
                                postButton("/admin/compos/${compo.id}/run-results/next") {
                                    icon("play")
                                    +" Start"
                                }
                            }
                        }
                    }
                }

                article {
                    table {
                        tbody {
                            steps.steps.forEachIndexed { index, step ->
                                tr {
                                    td(classes = "tight") {
                                        if (index == steps.current) {
                                            attributes["aria-busy"] = "true"
                                        } else {
                                            icon(step.icon())
                                        }
                                    }
                                    td(classes = "wide") {
                                        div { +step.title() }
                                        step.notes()?.let {
                                            small { +it }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
