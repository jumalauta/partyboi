package party.jml.partyboi.users

import kotlinx.html.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.templates.Page

object UserListPage {
    fun render(users: List<User>) = Page(
        title = "Users"
    ) {
        article {
            header { +"Users" }
            table {
                thead {
                    tr {
                        th {}
                        th { +"User name" }
                        th { +"Privileges" }
                        th { +"Email" }
                        th { +"Voting enabled" }
                    }
                }
                tbody {
                    users.forEach { user ->
                        tr {
                            td(classes = "settings") {
                                small { +user.id.toString() }
                            }
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
                                if (user.votingEnabled) +"Yes" else +"No"
                            }
                        }
                    }
                }
            }
        }
    }
}