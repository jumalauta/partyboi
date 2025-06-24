package party.jml.partyboi.voting.admin

import arrow.core.Option
import kotlinx.html.article
import kotlinx.html.header
import kotlinx.html.li
import kotlinx.html.ul
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.templates.Page
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
        @property:Field(label = "Number of keys")
        val numberOfKeys: Int,
    ) : Validateable<GenerateVoteKeySettings> {
        override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
            expectAtLeast("numberOfKeys", numberOfKeys, 1),
            expectAtMost("numberOfKeys", numberOfKeys, 1000),
        )

        companion object {
            val Empty = GenerateVoteKeySettings(72)
        }
    }
}