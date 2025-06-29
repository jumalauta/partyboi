package party.jml.partyboi.voting

import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Label
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.validation.MaxLength
import party.jml.partyboi.validation.MinLength
import party.jml.partyboi.validation.Validateable

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
    @MinLength(8)
    @MaxLength(8)
    val code: String
) : Validateable<VoteKeyForm> {
    companion object {
        val Empty: VoteKeyForm = VoteKeyForm(code = "")
    }
}