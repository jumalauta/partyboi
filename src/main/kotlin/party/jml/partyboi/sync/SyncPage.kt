package party.jml.partyboi.sync

import kotlinx.html.*
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.system.toIsoString
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.buttonGroup
import party.jml.partyboi.templates.components.buttonLink
import java.net.URI

object SyncPage {
    fun render(apiKey: String?, canSync: Boolean, remote: Form<RemoteInstance>, syncLog: List<SyncLogEntry>) =
        Page("Remote sync") {
            h1 { +"Remote sync" }

            if (canSync) {
                article {
                    header { +"Remote sync" }
                    buttonGroup {
                        buttonLink(href = "/sync/run") { +"Run" }
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
                article {
                    header { +"Log" }
                    table {
                        thead {
                            tr {
                                th { +"Entry" }
                                th { +"Start time" }
                                th { +"End time" }
                                th { +"Status" }
                            }
                            syncLog.forEach { entry ->
                                tr {
                                    td { +entry.description }
                                    td { +entry.startTime.toIsoString() }
                                    td { +entry.endTime?.toIsoString().orEmpty() }
                                    td {
                                        if (entry.hasEnded) {
                                            if (entry.isSuccess) {
                                                +"OK"
                                            } else {
                                                +entry.error.orEmpty()
                                            }
                                        } else {
                                            +"Running"
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
