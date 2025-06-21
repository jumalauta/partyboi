package party.jml.partyboi.users

import kotlinx.html.*
import party.jml.partyboi.auth.User
import party.jml.partyboi.auth.UserCredentials
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.voting.VoteKey

object UserEditPage {
    fun render(
        session: User,
        user: User,
        credentials: Form<UserCredentials>,
        voteKeys: List<VoteKey>,
    ) =
        Page("Edit user") {
            h1 { +"Edit user #${user.id}" }

            columns(
                {
                    renderForm(
                        title = "Credentials",
                        url = "/admin/users/${user.id}",
                        form = credentials,
                    )
                },
                {
                    article {
                        header { +"Vote keys" }
                        if (voteKeys.isEmpty()) {
                            p { +"User does not have any vote keys" }
                        } else {
                            p { +"Vote key(s) acquired by:" }
                            ul {
                                voteKeys.forEach { key ->
                                    li { +key.explain() }
                                }
                            }
                        }
                        buttonGroup {
                            labeledToggleButton(
                                toggled = user.votingEnabled,
                                icons = IconSet.voting,
                                urlPrefix = "/admin/users/${user.id}/voting",
                                label = if (user.votingEnabled) "Revoke voting rights" else "Grant voting rights",
                            )
                        }

                    }
                    article {
                        header { +"Permissions" }
                        buttonGroup {
                            labeledToggleButton(
                                toggled = user.isAdmin,
                                icons = IconSet.admin,
                                urlPrefix = "/admin/users/${user.id}/admin",
                                label = if (user.isAdmin) "Revoke admin rights" else "Grant admin rights",
                                disabled = session.id == user.id,
                            )
                        }
                    }
                    article {
                        cardHeader("Verification")
                        section {
                            if (user.email == null) {
                                +"User does not have an email address"
                            } else {
                                if (user.emailVerified) {
                                    +"User has a verified email address "
                                    a(href = "mailto:${user.email}") { +user.email }
                                } else {
                                    p {
                                        +"User has an email address "
                                        a(href = "mailto:${user.email}") { +user.email }
                                        +" but it is not verified"
                                    }
                                    p {
                                        a(href = "/admin/users/${user.id}/send-verification") {
                                            attributes.put("role", "button")
                                            +"Send verification email"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
}
