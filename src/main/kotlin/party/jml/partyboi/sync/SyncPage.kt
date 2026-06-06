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
                +"Sync copies data and uploaded files between two Partyboi instances over HTTP. "
                +"A typical setup has a pre-party instance that collects registrations and entries online, "
                +"and an on-site instance at the party that runs the compos. Before the party you pull the "
                +"pre-party data onto the on-site instance; after the party you push results back."
            }
            p {
                small {
                    +"Each instance plays one or two roles. The "
                    strong { +"data master" }
                    +" is the instance other instances read from — it must enable syncing and hand out a "
                    +"token. A "
                    strong { +"client" }
                    +" is an instance that has been told the master's address and token and can pull from "
                    +"or push to it. The same server can do both — generate a token for inbound sync "
                    em { +"and" }
                    +" point at another server for outbound sync."
                }
            }

            if (canSync) {
                article {
                    cardHeader("Sync now")
                    p {
                        +"Run a sync against the configured remote instance below. Both actions transfer "
                        +"every syncable table (users, compos, entries, votes, schedule, …) and any "
                        +"missing uploaded files. They run in the background — watch the log at the "
                        +"bottom of the page for progress."
                    }
                    ul {
                        li {
                            strong { +"Download" }
                            +" — pull data and files "
                            em { +"from" }
                            +" the remote into this instance. Use this on the on-site instance before "
                            +"the party to bring in everything the pre-party instance has collected. "
                            +"Existing rows with the same primary key are overwritten."
                        }
                        li {
                            strong { +"Upload" }
                            +" — push data and files "
                            em { +"from" }
                            +" this instance to the remote. Use this after the party to send results, "
                            +"votes and any entries added on-site back to the pre-party instance."
                        }
                    }
                    p {
                        small {
                            +"Both directions overwrite the target's rows where IDs collide, so only run "
                            +"the direction that matches which instance currently holds the truth."
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
                    +"Point this instance at another Partyboi server you want to sync with. Fill in "
                    +"that server's public address and the token its admin generated under "
                    em { +"Data master" }
                    +" on its own sync page. Without this, the "
                    em { +"Sync now" }
                    +" actions above are hidden."
                }
                renderForm(
                    url = "/sync/remote",
                    form = remote,
                )
            }

            article {
                cardHeader("Data master")
                p {
                    +"Controls whether "
                    em { +"other" }
                    +" Partyboi instances are allowed to sync with this one. Generating a token turns "
                    +"this instance into a data master: anyone who knows this server's address and the "
                    +"token can read and write its data through the sync API."
                }

                if (apiKey == null) {
                    p {
                        strong { +"Status: " }
                        +"syncing is disabled. No other instance can pull data from this server."
                    }
                    form("/sync/new-token", method = FormMethod.post) {
                        submitButton("Enable syncing and generate a token")
                    }
                } else {
                    p {
                        strong { +"Status: " }
                        +"this instance is waiting for sync requests. The previously issued token is "
                        +"still valid; generating a new one immediately invalidates the old one."
                    }
                    p {
                        small {
                            +"The token is shown only once — right after it is created. Copy it into "
                            +"the other instance's "
                            em { +"Remote instance configuration" }
                            +" straight away."
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
                            +"Recent sync attempts on this instance, in start order. Each table transfer "
                            +"and file transfer gets its own row, so a single Download or Upload run "
                            +"produces many entries."
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
                    +"Copy these two values into the "
                    em { +"Remote instance configuration" }
                    +" form on the "
                    strong { +"other" }
                    +" Partyboi instance — the one that should sync with this server. That instance "
                    +"will then be able to download data from, or upload data to, this server."
                }
                p {
                    small {
                        strong { +"This token is shown only once." }
                        +" There is no way to recover it later — if you lose it, generate a new one "
                        +"(which invalidates this one)."
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
