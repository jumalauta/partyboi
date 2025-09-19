package party.jml.partyboi.sync

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.data.Filesize
import party.jml.partyboi.form.submitButton
import party.jml.partyboi.system.displayDateTime
import party.jml.partyboi.templates.Page

object SyncAdminPage {
    fun render(configState: SymmetricDsConfigurationState, hosts: List<NodeHost>, tz: TimeZone) =
        Page("Sync settings") {
            h1 { +"Instance synchronization" }

            article {
                when (configState) {
                    SymmetricDsConfigurationState.MISSING -> {
                        +"SymmetricDS has not been installed."
                    }

                    SymmetricDsConfigurationState.NOT_CONFIGURED -> {
                        p { +"SymmetricDS was found but it hasn't been initialized." }
                        form(method = FormMethod.post, action = "/admin/sync/init") {
                            submitButton("Initialize SymmetricDS")
                        }
                    }

                    SymmetricDsConfigurationState.READY -> {
                        +"SymmetricDS is ready for use."
                    }
                }
            }

            hosts.forEach { host ->
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
                                td { +host.heartbeatTime.displayDateTime(tz) }
                            }
                            tr {
                                th {}
                                th { +"Last restart" }
                                td { +host.lastRestartTime.displayDateTime(tz) }
                            }
                            tr {
                                th {}
                                th { +"Created" }
                                td { +host.createTime.displayDateTime(tz) }
                            }
                        }
                    }
                }
            }
        }
}