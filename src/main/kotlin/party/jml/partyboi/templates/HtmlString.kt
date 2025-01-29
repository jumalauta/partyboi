package party.jml.partyboi.templates

import party.jml.partyboi.auth.User

data class HtmlString(
    val html: String
) : Renderable {
    override fun getHTML(user: User?, path: String): String = "<!DOCTYPE html>$html"
}