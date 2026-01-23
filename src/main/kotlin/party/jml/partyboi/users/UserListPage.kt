package party.jml.partyboi.users

import kotlinx.html.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.data.toPercentage
import party.jml.partyboi.templates.Page
import java.util.*

object UserListPage {
    fun render(users: List<User>, jmlCaptchaScores: Map<UUID, Double>) = Page(
        title = "Users"
    ) {
        article {
            header { +"Users" }
            table {
                thead {
                    tr {
                        th { +"User name" }
                        th { +"Privileges" }
                        th { +"Email" }
                        th { +"jmlCAPTCHA" }
                        th { +"Voting enabled" }
                    }
                }
                tbody {
                    users.forEach { user ->
                        tr {
                            td {
                                a(href = "/admin/users/${user.id}") {
                                    +user.name
                                }
                            }
                            td {
                                if (user.isAdmin) +"Admin" else +"-"
                            }
                            td {
                                user.email?.let { email ->
                                    a(href = "mailto:$email") { +email }
                                }
                            }
                            td {
                                +(jmlCaptchaScores[user.id]?.toPercentage() ?: "n/a")
                            }
                            td {
                                if (user.votingEnabled) +"Yes" else +"No"
                            }
                        }
                    }
                }
            }
        }
    }
}