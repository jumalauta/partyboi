package party.jml.partyboi.compos.admin

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.screen.admin.AdminScreenPage.renderWithScreenMonitoring
import party.jml.partyboi.screen.admin.postButton
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.icon
import party.jml.partyboi.templates.components.reloadSection

object CompoScreenRunningPage {
    fun render(
        compo: Compo,
        steps: CompoSteps,
    ) = Page("${compo.name} compo info screen") {
        h1 { +"${compo.name} compo info screen" }

        renderWithScreenMonitoring(true) {
            reloadSection {
                nav {
                    ul {
                        if (steps.current != null) {
                            li {
                                postButton("/admin/compos/${compo.id}/run/prev") {
                                    disabled = steps.current <= 0
                                    icon("arrow-left")
                                    +" Previous"
                                }
                            }
                            li {
                                postButton("/admin/compos/${compo.id}/run/next") {
                                    disabled = steps.current >= steps.steps.size - 1
                                    icon("arrow-right")
                                    +" Next"
                                }
                            }
                        } else {
                            li {
                                postButton("/admin/compos/${compo.id}/run/next") {
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