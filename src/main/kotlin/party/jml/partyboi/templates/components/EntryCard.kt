package party.jml.partyboi.templates.components

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.entries.EntryBase
import party.jml.partyboi.entries.Screenshot
import party.jml.partyboi.form.readonlyField

fun FlowContent.entryCard(
    entry: EntryBase,
    screenshot: Screenshot?,
    compos: List<Compo>,
    block: SECTION.() -> Unit = {}
) {
    article(classes = "entry") {
        figure {
            if (screenshot != null) {
                attributes["style"] = "background-image: url(${screenshot.externalUrl()})"
            }
        }
        section {
            table {
                readonlyField("Title", entry.title)
                readonlyField("Author", entry.author)
                readonlyField("Compo", compos.find { it.id == entry.compoId }?.name ?: entry.compoId.toString())
            }
            block()
        }
    }
}