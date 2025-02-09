package party.jml.partyboi.voting

import kotlinx.html.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.Page

object RegisterVoteKeyPage {
    fun render(form: Form<VoteKeyForm>) = Page("Enable voting") {
        renderForm(
            title = "Enable voting",
            url = "/vote/register",
            form = form,
            submitButtonLabel = "Register your vote key"
        )
    }
}

data class VoteKeyForm(
    @property:Field(label = "Vote key")
    val code: String
) : Validateable<VoteKeyForm> {
    override fun validationErrors() = listOf(
        expectMinLength("code", code, 8),
        expectMaxLength("code", code, 8),
    )

    companion object {
        val Empty: VoteKeyForm = VoteKeyForm(code = "")
    }
}