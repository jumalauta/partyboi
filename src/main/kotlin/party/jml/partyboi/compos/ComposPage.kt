package party.jml.partyboi.compos

import kotlinx.html.*
import party.jml.partyboi.settings.VoteSettings
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.components.markdown

object ComposPage {
    fun render(generalRules: GeneralRules, compos: List<Compo>, voteSettings: VoteSettings) =
        Page("Compos") {
            h1 { +"Compos" }

            article {
                id = "generalRules"
                cardHeader("General rules")
                markdown(generalRules.rules)

                h2 { +"Voting" }
                voteCounting(voteSettings)
            }

            if (compos.isEmpty()) {
                article {
                    id = "noCompos"
                    +"No compos have not been published."
                }
            }

            compos.filter { it.visible }.forEach { compo ->
                article(classes = "compo") {
                    cardHeader(compo.name)
                    markdown(compo.rules)

                    nav {
                        ul {
                            if (compo.allowSubmit) {
                                li {
                                    a(href = "/entries/submit/${compo.id}", classes = "submitEntry") {
                                        +"Submit an entry"
                                    }
                                }
                            }
                            if (compo.allowVote) {
                                li {
                                    a(href = "/vote", classes = "vote") {
                                        +"Vote"
                                    }
                                }
                            }
                            if (compo.publicResults) {
                                li {
                                    a(href = "/results#${compo.id}", classes = "results") {
                                        +"Results"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    fun FlowContent.voteCounting(settings: VoteSettings) {
        ul {
            li { +"${settings.automaticVoteKeys.label}." }
            li { +"Points are given between ${settings.minimumPoints} to ${settings.maximumPoints}." }
            li { +"${settings.emptyVoteHandling.label}." }
            li { +"${settings.scoringMethod.label}." }
        }
    }
}