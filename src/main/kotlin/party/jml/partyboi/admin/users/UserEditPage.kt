package party.jml.partyboi.admin.users

import kotlinx.html.*
import party.jml.partyboi.auth.UserCredentials
import party.jml.partyboi.auth.User
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.dataForm
import party.jml.partyboi.form.renderFields
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.voting.VoteKey

object UserEditPage {
    fun render(session: User, user: User, credentials: Form<UserCredentials>, voteKeys: List<VoteKey>) =
        Page("Edit user") {
            h1 { +"Edit user #${user.id}" }

            columns(
                {
                    article {
                        header { +"Credentials" }
                        dataForm("/admin/users/${user.id}") {
                            fieldSet {
                                renderFields(credentials)
                            }
                            footer { submitInput { value = "Save changes" } }
                        }
                    }
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
                }
            )
        }
}
