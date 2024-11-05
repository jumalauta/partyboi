package party.jml.partyboi.templates.components

import kotlinx.html.*

fun ARTICLE.cardHeader(text: String) {
    header { h3 { +text } }
}