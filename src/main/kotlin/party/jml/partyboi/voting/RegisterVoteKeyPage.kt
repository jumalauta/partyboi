package party.jml.partyboi.voting

import party.jml.partyboi.data.Validateable
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Label
import party.jml.partyboi.form.renderForm
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
    @Label("Vote key")
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