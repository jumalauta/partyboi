package party.jml.partyboi.sync

import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.system.TimeService
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonGroup
import party.jml.partyboi.templates.components.buttonLink
import party.jml.partyboi.templates.components.timestamp
import java.net.URI

object SyncPage {
    fun render(apiKey: String?, canSync: Boolean, remote: Form<RemoteInstance>, syncLog: List<SyncLogEntry>) =
        Page("Remote sync") {
            h1 { +"Remote sync" }

            if (canSync) {
                article {
                    header { +"Remote sync" }
                    buttonGroup {
                        buttonLink(href = "/sync/download") { +"Download" }
                        buttonLink(href = "/sync/upload") { +"Upload" }
                    }
                }
            }

            renderForm(
                title = "Remote instance configuration",
                url = "/sync/remote",
                form = remote,
            )

            article {
                header { +"Data master" }

                if (apiKey == null) {
                    p {
                        +"This instance does not allow other instances to download its data."
                    }
                    form("/sync/new-token", method = FormMethod.post) {
                        submitButton("Enable syncing and generate a token")
                    }
                } else {
                    p {
                        +"This instance is waiting for sync requests."
                    }
                    form("/sync/new-token", method = FormMethod.post) {
                        submitButton("Generate a new token")
                    }
                }
            }

            if (syncLog.isNotEmpty()) {
                val tz = TimeService.timeZone()
                article {
                    header { +"Log" }
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
        Page("New API Token") {
            h1 { +"New API Token created" }

            article {
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
                a(href = "/sync") { +"Get back" }
            }
        }
}
