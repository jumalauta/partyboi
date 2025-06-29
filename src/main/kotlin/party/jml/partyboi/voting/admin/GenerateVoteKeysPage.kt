package party.jml.partyboi.voting.admin

import kotlinx.html.article
import kotlinx.html.header
import kotlinx.html.li
import kotlinx.html.ul
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.Label
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
import party.jml.partyboi.validation.Max
import party.jml.partyboi.validation.Min
import party.jml.partyboi.validation.Validateable
import party.jml.partyboi.voting.VoteKeyRow

object GenerateVoteKeysPage {
    fun renderForm(form: Form<GenerateVoteKeySettings>) = Page("Generate vote keys") {
        renderForm(
            title = "Generate vote keys",
            url = "/admin/voting/generate",
            form = form,
            submitButtonLabel = "Generate keys",
        )
    }

    fun renderTickets(tickets: List<VoteKeyRow>) = Page("Print vote keys") {
        article(classes = "votekeys") {
            header { +"Vote keys" }
            ul {
                tickets.mapNotNull { it.key.id }.forEach {
                    li { +it }
                }
            }
        }
    }

    data class GenerateVoteKeySettings(
        @Label("Number of keys")
        @Min(1)
        @Max(1000)
        val numberOfKeys: Int,
    ) : Validateable<GenerateVoteKeySettings> {
        companion object {
            val Empty = GenerateVoteKeySettings(72)
        }
    }
}
