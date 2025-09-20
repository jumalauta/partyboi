package party.jml.partyboi.sync

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.timestamp
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.Validateable
import kotlin.time.ExperimentalTime

object SyncAdminPage {
    @OptIn(ExperimentalTime::class)
    fun render(
        configState: SymmetricDsConfigurationState,
        host: NodeHost?,
        tz: TimeZone,
        nodeSecurities: List<NodeSecurity>,
        newNodeForm: Form<NewNodeForm>
    ) =
        Page("Sync settings") {
            h1 { +"Instance synchronization" }

            when (configState) {
                SymmetricDsConfigurationState.MISSING -> {
                    article { +"SymmetricDS has not been installed." }
                }

                SymmetricDsConfigurationState.NOT_CONFIGURED -> {
                    article {
                        p { +"SymmetricDS was found but it hasn't been initialized." }
                        form(method = FormMethod.post, action = "/admin/sync/init") {
                            submitButton("Initialize SymmetricDS")
                        }
                    }
                }

                SymmetricDsConfigurationState.READY -> {
                    article {
                        p {
                            +"SymmetricDS is ready for use."
                            host?.let { host ->
                                +" Received a heartbeat "
                                timestamp(host.heartbeatTime, tz)
                                +"."
                            }
                        }
                        form(method = FormMethod.post, action = "/admin/sync/init") {
                            submitButton("Reinitialize SymmetricDS")
                        }
                    }

                    h1 { +"Clients" }

                    if (nodeSecurities.isNotEmpty()) {
                        article {
                            table {
                                tr {
                                    th { +"External ID" }
                                    th { +"Registration" }
                                    th { +"Sync" }
                                }
                                nodeSecurities.forEach { security ->
                                    tr {
                                        td { +security.node.id }
                                        td {
                                            if (security.registrationEnabled) {
                                                +"Pending"
                                            } else {
                                                +"Registered"
                                            }
                                        }
                                        td {
                                            switchLink(
                                                toggled = security.node.syncEnabled,
                                                labelOn = "Enabled",
                                                labelOff = "Disabled",
                                                urlPrefix = "/admin/sync/${security.node.id}/syncEnabled",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    renderForm("/admin/sync", newNodeForm, submitButtonLabel = "Add new node")
                }
            }

            host?.let { host ->
                h1 { +"Host" }
                article {
                    table {
                        tbody {
                            tr {
                                th { +"SymmetricDS" }
                                th { +"Version" }
                                td { +host.symmetricVersion }
                            }
                            tr {
                                th { +"Operating system" }
                                th { +"Name" }
                                td { +host.osName }
                            }
                            tr {
                                th {}
                                th { +"Architecture" }
                                td { +host.osArch }
                            }
                            tr {
                                th {}
                                th { +"Version" }
                                td { +host.osVersion }
                            }
                            tr {
                                th { +"System" }
                                th { +"Available processors" }
                                td { +host.availableProcessors.toString() }
                            }
                            tr {
                                th {}
                                th { +"Free memory" }
                                td { +Filesize.humanFriendly(host.freeMemoryBytes) }
                            }
                            tr {
                                th {}
                                th { +"Total memory" }
                                td { +Filesize.humanFriendly(host.totalMemoryBytes) }
                            }
                            tr {
                                th {}
                                th { +"Max memory" }
                                td { +Filesize.humanFriendly(host.maxMemoryBytes) }
                            }
                            tr {
                                th { +"Runtime" }
                                th { +"Java version" }
                                td { +host.javaVersion }
                            }
                            tr {
                                th {}
                                th { +"Java vendor" }
                                td { +host.javaVendor }
                            }
                            tr {
                                th {}
                                th { +"JDBC version" }
                                td { +host.jdbcVersion }
                            }
                            tr {
                                th { +"Time" }
                                th { +"Heartbeat" }
                                td { timestamp(host.heartbeatTime, tz) }
                            }
                            tr {
                                th {}
                                th { +"Last restart" }
                                td { timestamp(host.lastRestartTime, tz) }
                            }
                            tr {
                                th {}
                                th { +"Created" }
                                td { timestamp(host.createTime, tz) }
                            }
                        }
                    }
                }
            }
        }
}

data class NewNodeForm(
    @Field(label = "External ID")
    @NotEmpty
    val nodeId: String,
) : Validateable<NewNodeForm> {
    companion object {
        val Empty = NewNodeForm("")
    }
}