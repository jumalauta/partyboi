package party.jml.partyboi.sync

import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.system.TimeService
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonGroup
import party.jml.partyboi.templates.components.buttonLink
import party.jml.partyboi.templates.components.cardHeader
import party.jml.partyboi.templates.components.timestamp
import java.net.URI

object SyncPage {
    fun render(apiKey: String?, canSync: Boolean, remote: Form<RemoteInstance>, syncLog: List<SyncLogEntry>) =
        Page("Remote sync") {
            h1 { +"Remote sync" }

            p {
                +"Sync copies data and files between two Partyboi instances over HTTP — pull "
                +"entries from a pre-party instance before the event, push results back after."
            }
            p {
                small {
                    +"Each instance has a role. A "
                    strong { +"data master" }
                    +" enables syncing and hands out a token; a "
                    strong { +"client" }
                    +" uses a master's address and token to pull from or push to it. One server can be both."
                }
            }

            if (canSync) {
                article {
                    cardHeader("Sync now")
                    p {
                        +"Both actions transfer every syncable table and any missing files, and run "
                        +"in the background — watch the log below."
                    }
                    ul {
                        li {
                            strong { +"Download" }
                            +" — pull from the remote into this instance (e.g. before the party)."
                        }
                        li {
                            strong { +"Upload" }
                            +" — push from this instance to the remote (e.g. after the party)."
                        }
                    }
                    p {
                        small {
                            +"Both directions overwrite the target's rows where IDs collide, so run only "
                            +"the direction whose source holds the current data."
                        }
                    }
                    buttonGroup {
                        buttonLink(href = "/sync/download") { +"Download" }
                        buttonLink(href = "/sync/upload") { +"Upload" }
                    }
                }
            }

            article {
                cardHeader("Remote instance configuration")
                p {
                    +"Point this instance at another Partyboi server: enter its public address and a "
                    +"token it generated under "
                    em { +"Data master" }
                    +". Without this, the "
                    em { +"Sync now" }
                    +" actions are hidden."
                }
                renderForm(
                    url = "/sync/remote",
                    form = remote,
                )
            }

            article {
                cardHeader("Data master")
                p {
                    +"Lets "
                    em { +"other" }
                    +" instances sync with this one. Anyone with this server's address and the token "
                    +"can read and write its data over the sync API."
                }

                if (apiKey == null) {
                    p {
                        strong { +"Status: " }
                        +"syncing is disabled. No other instance can reach this server."
                    }
                    form("/sync/new-token", method = FormMethod.post) {
                        submitButton("Enable syncing and generate a token")
                    }
                } else {
                    p {
                        strong { +"Status: " }
                        +"waiting for sync requests. The current token stays valid; generating a new "
                        +"one invalidates it."
                    }
                    p {
                        small {
                            +"The token is shown only once, right after creation — copy it into the "
                            +"other instance's "
                            em { +"Remote instance configuration" }
                            +" immediately."
                        }
                    }
                    form("/sync/new-token", method = FormMethod.post) {
                        submitButton("Generate a new token")
                    }
                }
            }

            if (syncLog.isNotEmpty()) {
                val tz = TimeService.timeZone()
                article {
                    cardHeader("Log")
                    p {
                        small {
                            +"Recent sync attempts in start order. Each table and file transfer is its "
                            +"own row, so one run produces many."
                        }
                    }
                    table {
                        thead {
                            tr {
                                th { +"Status" }
                                th { +"Entry" }
                                th { +"Time" }
                            }
                        }
                        tbody {
                            syncLog.forEach { entry ->
                                val status = if (entry.hasEnded) {
                                    if (entry.isSuccess) {
                                        "OK"
                                    } else {
                                        "Error"
                                    }
                                } else {
                                    "Running"
                                }
                                tr(classes = "sync-status-${status.lowercase()}") {
                                    td { +status }
                                    td { +entry.description }
                                    td {
                                        if (entry.endTime != null) {
                                            +"Finished "
                                            timestamp(entry.endTime, tz)
                                        } else {
                                            +"Started "
                                            timestamp(entry.startTime, tz)
                                        }
                                    }
                                }
                                entry.error?.let {
                                    tr(classes = "sync-status-error") {
                                        td {
                                            colSpan = "3"
                                            +entry.error
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    fun renderNewToken(host: URI, token: String) =
        Page("New API token") {
            h1 { +"New API token created" }

            article {
                p {
                    +"Copy these values into the "
                    em { +"Remote instance configuration" }
                    +" form on the "
                    strong { +"other" }
                    +" Partyboi instance — the one that should sync with this server."
                }
                p {
                    small {
                        strong { +"This token is shown only once." }
                        +" If you lose it, generate a new one (which invalidates this one)."
                    }
                }
                table {
                    tbody {
                        tr {
                            th { +"Remote address:" }
                            td { small { +host.toString() } }
                        }
                        tr {
                            th { +"Token:" }
                            td { small { +token } }
                        }
                    }
                }
                a(href = "/sync") { +"Back to sync" }
            }
        }
}
