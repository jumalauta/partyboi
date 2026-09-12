package party.jml.partyboi.templates.components

import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.span
import party.jml.partyboi.entries.EntryBase

fun FlowOrPhrasingContent.entryChips(entry: EntryBase) = entryChips(entry.remote, entry.aiGenerated)

fun FlowOrPhrasingContent.entryChips(remote: Boolean, aiGenerated: Boolean) {
    if (aiGenerated) chip("AI")
    if (remote) chip("Remote")
}

private fun FlowOrPhrasingContent.chip(label: String) {
    +" "
    span(classes = "chip") { +label }
}
